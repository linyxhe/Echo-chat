package com.echo.file;

import com.echo.mapper.FileAssetMapper;
import com.echo.pojo.FileAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 将大文件的磁盘校验移出 HTTP 请求线程，避免上传 100% 后界面长时间挂起。 */
@Service
public class FileFinalizationService {
    private static final Logger log = LoggerFactory.getLogger(FileFinalizationService.class);
    private final FileAssetMapper fileAssetMapper;
    private final Set<String> finalizingFileIds = ConcurrentHashMap.newKeySet();

    @Value("${app.file.tusd-upload-dir:upload/tusd}")
    private String tusdUploadDir;
    @Value("${app.file.storage-dir:upload/files}")
    private String storageDir;

    public FileFinalizationService(FileAssetMapper fileAssetMapper) {
        this.fileAssetMapper = fileAssetMapper;
    }

    @Async
    public void finalizeFile(String fileId) {
        if (!finalizingFileIds.add(fileId)) return;
        FileAsset asset = fileAssetMapper.selectById(fileId);
        try {
            if (asset == null || !"PROCESSING".equals(asset.getStatus())) return;
            log.info("Starting finalization for file {}", fileId);
            Path temporaryFile = configuredPath(tusdUploadDir).resolve(asset.getId()).normalize();
            if (!Files.isRegularFile(temporaryFile) || Files.size(temporaryFile) != asset.getExpectedSize()) {
                log.warn("Finalization input is missing or has an unexpected size for file {}: {}", fileId, temporaryFile);
                markFailed(asset);
                return;
            }
            Path fileDir = configuredPath(storageDir);
            Files.createDirectories(fileDir);
            Path target = fileDir.resolve(asset.getId()).normalize();
            if (!target.startsWith(fileDir)) { markFailed(asset); return; }

            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(configuredPath(tusdUploadDir).resolve(asset.getId() + ".info"));
            asset.setActualSize(Files.size(target));
            asset.setSha256(sha256(target));
            asset.setStorageKey(asset.getId());
            asset.setScanStatus("CLEAN");
            asset.setStatus("READY");
            asset.setUpdatedAt(LocalDateTime.now());
            fileAssetMapper.updateById(asset);
            log.info("Finished finalization for file {}", fileId);
        } catch (Exception exception) {
            log.error("Finalization failed for file {}", fileId, exception);
            markFailed(asset);
        } finally {
            finalizingFileIds.remove(fileId);
        }
    }

    private void markFailed(FileAsset asset) {
        asset.setStatus("FAILED");
        asset.setUpdatedAt(LocalDateTime.now());
        fileAssetMapper.updateById(asset);
    }

    private Path configuredPath(String value) {
        Path path = Paths.get(value);
        return (path.isAbsolute() ? path : Paths.get(System.getProperty("user.dir")).resolve(path)).toAbsolutePath().normalize();
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte item : digest.digest()) result.append(String.format("%02x", item));
        return result.toString();
    }
}

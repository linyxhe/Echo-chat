package com.echo.clientrelease;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.echo.mapper.ClientReleaseMapper;
import com.echo.pojo.ClientRelease;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientReleaseService {

    private static final String WINDOWS = "WINDOWS";
    private static final String ANDROID = "ANDROID";

    private final ClientReleaseMapper clientReleaseMapper;

    @Value("${app.client-release.storage-dir:upload/client-releases}")
    private String storageDir;

    @Value("${app.client-release.max-size:1073741824}")
    private long maxFileSize;

    public ClientReleaseService(ClientReleaseMapper clientReleaseMapper) {
        this.clientReleaseMapper = clientReleaseMapper;
    }

    public List<Map<String, Object>> listPublished() {
        Map<String, ClientRelease> newestByPlatform = new LinkedHashMap<>();
        List<ClientRelease> releases = clientReleaseMapper.selectList(new QueryWrapper<ClientRelease>()
                .eq("published", true)
                .orderByDesc("published_at")
                .orderByDesc("id"));
        for (ClientRelease release : releases) {
            newestByPlatform.putIfAbsent(release.getPlatform(), release);
        }
        return newestByPlatform.values().stream().map(this::toPublicView).toList();
    }

    public List<Map<String, Object>> listAll() {
        List<ClientRelease> releases = clientReleaseMapper.selectList(new QueryWrapper<ClientRelease>()
                .orderByDesc("created_at").orderByDesc("id"));
        return releases.stream().map(this::toAdminView).toList();
    }

    @Transactional
    public Map<String, Object> upload(Long adminId, String platform, String version, String releaseNotes,
                                      boolean publish, MultipartFile file) {
        String normalizedPlatform = normalizePlatform(platform);
        validateUpload(normalizedPlatform, version, file);

        String safeFileName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String storageKey = UUID.randomUUID().toString();
        Path target = storageDirectory().resolve(storageKey).normalize();
        if (!target.startsWith(storageDirectory())) throw new IllegalStateException("Invalid release storage path");

        try {
            Files.createDirectories(storageDirectory());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            LocalDateTime now = LocalDateTime.now();
            ClientRelease release = new ClientRelease();
            release.setPlatform(normalizedPlatform);
            release.setVersion(version.trim());
            release.setFileName(safeFileName);
            release.setStorageKey(storageKey);
            release.setFileSize(Files.size(target));
            release.setSha256(sha256(target));
            release.setReleaseNotes(StringUtils.hasText(releaseNotes) ? releaseNotes.trim() : null);
            release.setPublished(false);
            release.setCreatedBy(adminId);
            release.setCreatedAt(now);
            release.setUpdatedAt(now);
            clientReleaseMapper.insert(release);

            if (publish) publish(release.getId());
            return toAdminView(clientReleaseMapper.selectById(release.getId()));
        } catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new IllegalArgumentException("安装包保存失败，请重试");
        }
    }

    @Transactional
    public boolean publish(Long releaseId) {
        ClientRelease release = clientReleaseMapper.selectById(releaseId);
        if (release == null) return false;
        LocalDateTime now = LocalDateTime.now();
        clientReleaseMapper.update(null, new UpdateWrapper<ClientRelease>()
                .eq("platform", release.getPlatform())
                .set("published", false)
                .set("published_at", null)
                .set("updated_at", now));
        release.setPublished(true);
        release.setPublishedAt(now);
        release.setUpdatedAt(now);
        clientReleaseMapper.updateById(release);
        return true;
    }

    @Transactional
    public boolean delete(Long releaseId) {
        ClientRelease release = clientReleaseMapper.selectById(releaseId);
        if (release == null) return false;
        clientReleaseMapper.deleteById(releaseId);
        try { Files.deleteIfExists(resolveFile(release)); } catch (IOException ignored) { }
        return true;
    }

    public ClientRelease findPublished(Long releaseId) {
        ClientRelease release = clientReleaseMapper.selectById(releaseId);
        return release != null && Boolean.TRUE.equals(release.getPublished()) ? release : null;
    }

    public Path resolveFile(ClientRelease release) {
        if (release == null || !StringUtils.hasText(release.getStorageKey())) return null;
        Path file = storageDirectory().resolve(release.getStorageKey()).normalize();
        return file.startsWith(storageDirectory()) ? file : null;
    }

    private Map<String, Object> toPublicView(ClientRelease release) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", release.getId());
        view.put("platform", release.getPlatform());
        view.put("version", release.getVersion());
        view.put("fileName", release.getFileName());
        view.put("fileSize", release.getFileSize());
        view.put("releaseNotes", release.getReleaseNotes());
        view.put("publishedAt", release.getPublishedAt());
        view.put("downloadUrl", "/client-releases/" + release.getId() + "/download");
        return view;
    }

    private Map<String, Object> toAdminView(ClientRelease release) {
        Map<String, Object> view = new LinkedHashMap<>(toPublicView(release));
        view.put("published", release.getPublished());
        view.put("sha256", release.getSha256());
        view.put("createdAt", release.getCreatedAt());
        view.put("createdBy", release.getCreatedBy());
        return view;
    }

    private void validateUpload(String platform, String version, MultipartFile file) {
        if (!StringUtils.hasText(version) || version.trim().length() > 64) {
            throw new IllegalArgumentException("请填写不超过 64 个字符的版本号");
        }
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择安装包文件");
        if (file.getSize() > maxFileSize) throw new IllegalArgumentException("安装包超过服务器允许的大小");
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("安装包文件名无效");
        String lowerName = name.toLowerCase(Locale.ROOT);
        boolean extensionMatches = WINDOWS.equals(platform)
                ? lowerName.endsWith(".exe") || lowerName.endsWith(".msi") || lowerName.endsWith(".zip")
                : lowerName.endsWith(".apk");
        if (!extensionMatches) {
            throw new IllegalArgumentException(WINDOWS.equals(platform)
                    ? "Windows 客户端只允许 .exe、.msi 或 .zip 文件"
                    : "Android 客户端只允许 .apk 文件");
        }
    }

    private String normalizePlatform(String platform) {
        String value = platform == null ? "" : platform.trim().toUpperCase(Locale.ROOT);
        if (!WINDOWS.equals(value) && !ANDROID.equals(value)) {
            throw new IllegalArgumentException("不支持的客户端平台");
        }
        return value;
    }

    private Path storageDirectory() {
        Path configured = Paths.get(storageDir);
        return (configured.isAbsolute() ? configured : Paths.get(System.getProperty("user.dir")).resolve(configured))
                .toAbsolutePath().normalize();
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder();
            for (byte item : digest.digest()) result.append(String.format("%02x", item));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

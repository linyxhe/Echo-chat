package com.echo.file;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.file.dto.ChatUploadIntentRequest;
import com.echo.mapper.FileAssetMapper;
import com.echo.mapper.FriendshipMapper;
import com.echo.pojo.FileAsset;
import com.echo.pojo.Friendship;
import com.echo.service.GroupService;
import com.echo.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FileServiceImpl implements FileService {

    private static final Pattern FILE_URL_PATTERN = Pattern.compile("^/files/([0-9a-fA-F-]{36})/content(?:\\?.*)?$");

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private final FileAssetMapper fileAssetMapper;
    private final FriendshipMapper friendshipMapper;
    private final FileFinalizationService fileFinalizationService;
    private final GroupService groupService;

    @Value("${app.file.max-size:2147483648}")
    private long maxFileSize;

    @Value("${app.file.intent-ttl-minutes:60}")
    private long intentTtlMinutes;

    @Value("${app.file.tus-endpoint:http://localhost:1080/files/}")
    private String tusEndpoint;

    @Value("${app.file.tusd-upload-dir:upload/tusd}")
    private String tusdUploadDir;

    @Value("${app.file.storage-dir:upload/files}")
    private String storageDir;

    public FileServiceImpl(FileAssetMapper fileAssetMapper, FriendshipMapper friendshipMapper,
                           FileFinalizationService fileFinalizationService, GroupService groupService) {
        this.fileAssetMapper = fileAssetMapper;
        this.friendshipMapper = friendshipMapper;
        this.fileFinalizationService = fileFinalizationService;
        this.groupService = groupService;
    }

    @Override
    public Result<Object> createChatUploadIntent(Long ownerId, ChatUploadIntentRequest request) {
        if (ownerId == null || request == null
                || !StringUtils.hasText(request.getFileName()) || request.getSize() == null) {
            return Result.fail("上传参数不完整");
        }
        // 目标二选一：receiverId（好友）或 groupId（群）
        boolean toGroup = request.getGroupId() != null;
        boolean toChat = request.getReceiverId() != null;
        if (toGroup == toChat) return Result.fail("上传参数不完整");
        if (request.getSize() <= 0 || request.getSize() > maxFileSize) {
            return Result.fail("文件大小不符合限制");
        }
        Long targetId;
        String purpose;
        if (toGroup) {
            if (!isGroupMember(ownerId, request.getGroupId())) return Result.fail("不是群成员，无法上传文件");
            targetId = request.getGroupId();
            purpose = "GROUP";
        } else {
            if (request.getReceiverId().equals(ownerId)) return Result.fail("不能向自己创建聊天文件上传");
            if (!isFriend(ownerId, request.getReceiverId())) return Result.fail("未添加该好友，请先添加后再发送文件");
            targetId = request.getReceiverId();
            purpose = "CHAT";
        }

        String safeName = Paths.get(request.getFileName()).getFileName().toString();
        if (!StringUtils.hasText(safeName) || safeName.length() > 255) return Result.fail("文件名不合法");

        LocalDateTime now = LocalDateTime.now();
        FileAsset asset = new FileAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setOwnerId(ownerId);
        asset.setReceiverId(targetId);
        asset.setPurpose(purpose);
        asset.setOriginalName(safeName);
        asset.setContentType(StringUtils.hasText(request.getContentType()) ? request.getContentType() : "application/octet-stream");
        asset.setExpectedSize(request.getSize());
        asset.setUploadToken(randomToken());
        asset.setAccessToken(randomToken());
        asset.setStatus("UPLOADING");
        asset.setScanStatus("PENDING");
        asset.setExpiresAt(now.plusMinutes(intentTtlMinutes));
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        fileAssetMapper.insert(asset);

        Map<String, Object> data = new HashMap<>();
        data.put("fileId", asset.getId());
        data.put("uploadToken", asset.getUploadToken());
        data.put("tusEndpoint", tusEndpoint);
        data.put("expiresAt", asset.getExpiresAt());
        data.put("maxFileSize", maxFileSize);
        return Result.success(data);
    }

    @Override
    public Result<Object> uploadSmallFile(Long ownerId, MultipartFile file, Long receiverId) {
        if (ownerId == null) return Result.fail("未登录");
        if (file == null || file.isEmpty()) return Result.fail("文件为空");
        long size = file.getSize();
        if (size <= 0 || size > maxFileSize) return Result.fail("文件大小不符合限制");
        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "file";
        String safeName = Paths.get(originalName).getFileName().toString();
        if (!StringUtils.hasText(safeName) || safeName.length() > 255) return Result.fail("文件名不合法");

        Long receiver = (receiverId != null && receiverId > 0) ? receiverId : null;
        if (receiver != null) {
            if (receiver.equals(ownerId)) return Result.fail("不能向自己发送文件");
            if (!isFriend(ownerId, receiver)) return Result.fail("未添加该好友，请先添加后再发送文件");
        }
        return storeSmallFile(ownerId, file, safeName, size, receiver, receiver != null ? "CHAT" : "GENERIC");
    }

    @Override
    public Result<Object> uploadSmallFileToGroup(Long ownerId, MultipartFile file, Long groupId) {
        if (ownerId == null) return Result.fail("未登录");
        if (groupId == null) return Result.fail("参数缺失");
        if (!isGroupMember(ownerId, groupId)) return Result.fail("不是群成员，无法上传文件");
        if (file == null || file.isEmpty()) return Result.fail("文件为空");
        long size = file.getSize();
        if (size <= 0 || size > maxFileSize) return Result.fail("文件大小不符合限制");
        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "file";
        String safeName = Paths.get(originalName).getFileName().toString();
        if (!StringUtils.hasText(safeName) || safeName.length() > 255) return Result.fail("文件名不合法");
        return storeSmallFile(ownerId, file, safeName, size, groupId, "GROUP");
    }

    private Result<Object> storeSmallFile(Long ownerId, MultipartFile file, String safeName, long size,
                                          Long receiverId, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        FileAsset asset = new FileAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setOwnerId(ownerId);
        asset.setReceiverId(receiverId);
        asset.setPurpose(purpose);
        asset.setOriginalName(safeName);
        asset.setContentType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream");
        asset.setExpectedSize(size);
        asset.setUploadToken(randomToken());
        asset.setAccessToken(randomToken());
        asset.setStatus("UPLOADING");
        asset.setScanStatus("PENDING");
        asset.setExpiresAt(now.plusMinutes(intentTtlMinutes));
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        fileAssetMapper.insert(asset);

        try {
            Path fileDir = fileStorageDir();
            Files.createDirectories(fileDir);
            Path target = fileDir.resolve(asset.getId()).normalize();
            if (!target.startsWith(fileDir)) throw new IOException("invalid storage path");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            asset.setActualSize(Files.size(target));
            asset.setSha256(sha256(target));
            asset.setStorageKey(asset.getId());
            asset.setScanStatus("CLEAN");
            asset.setStatus("READY");
            asset.setUpdatedAt(LocalDateTime.now());
            fileAssetMapper.updateById(asset);
        } catch (Exception e) {
            try { Files.deleteIfExists(fileStorageDir().resolve(asset.getId())); } catch (IOException ignored) { }
            fileAssetMapper.deleteById(asset.getId());
            log.warn("Small file upload failed for asset {}: {}", asset.getId(), e.getMessage());
            return Result.fail("文件保存失败");
        }
        return Result.success(buildFileResponse(asset));
    }

    @Override
    public Result<Object> completeUpload(Long ownerId, String fileId) {
        FileAsset asset = findOwnedAsset(ownerId, fileId);
        if (asset == null) return Result.fail("上传任务不存在或无权限");
        if ("READY".equals(asset.getStatus()) || "PROCESSING".equals(asset.getStatus()) || "UPLOADING".equals(asset.getStatus())) return Result.success(buildFileResponse(asset));
        if (!"UPLOADED".equals(asset.getStatus())) return Result.fail("文件尚未上传完成");
        if (asset.getExpiresAt().isBefore(LocalDateTime.now())) return Result.fail("上传任务已过期");
        asset.setStatus("PROCESSING");
        asset.setUpdatedAt(LocalDateTime.now());
        fileAssetMapper.updateById(asset);
        fileFinalizationService.finalizeFile(asset.getId());
        return Result.success(buildFileResponse(asset));
    }

    @Override
    public Result<Object> getUploadStatus(Long ownerId, String fileId) {
        FileAsset asset = findOwnedAsset(ownerId, fileId);
        if (asset != null && "PROCESSING".equals(asset.getStatus())) {
            // Re-submit processing after a restart so browser refresh can recover it.
            fileFinalizationService.finalizeFile(asset.getId());
        }
        if (asset == null) return Result.fail("上传任务不存在或无权限");
        return Result.success(buildFileResponse(asset));
    }

    @Override
    public Result<Object> cancelUpload(Long ownerId, String fileId) {
        FileAsset asset = findOwnedAsset(ownerId, fileId);
        if (asset == null) return Result.fail("上传任务不存在或无权限");
        if ("READY".equals(asset.getStatus())) return Result.fail("已完成的文件不能取消");
        deleteTusdTemporaryFiles(asset.getId());
        asset.setStatus("CANCELLED");
        asset.setUpdatedAt(LocalDateTime.now());
        fileAssetMapper.updateById(asset);
        return Result.success();
    }

    @Override
    public Map<String, Object> handleTusHook(Map<String, Object> hookRequest) {
        String type = stringValue(hookRequest.get("Type"));
        Map<String, Object> upload = nestedMap(hookRequest, "Event", "Upload");
        Map<String, Object> metadata = nestedMap(upload, "MetaData");
        String fileId = stringValue(metadata.get("fileId"));
        String uploadToken = stringValue(metadata.get("uploadToken"));

        if ("pre-create".equals(type)) {
            FileAsset asset = fileAssetMapper.selectById(fileId);
            long requestedSize = longValue(upload.get("Size"));
            boolean valid = asset != null && "UPLOADING".equals(asset.getStatus())
                    && asset.getExpiresAt().isAfter(LocalDateTime.now())
                    && asset.getUploadToken().equals(uploadToken)
                    && asset.getExpectedSize() == requestedSize;
            if (!valid) return rejectedTusHook("上传意图无效、已过期或文件大小不匹配");

            Map<String, Object> changeFileInfo = new HashMap<>();
            changeFileInfo.put("ID", asset.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("ChangeFileInfo", changeFileInfo);
            return response;
        }

        if (("pre-finish".equals(type) || "post-finish".equals(type)) && StringUtils.hasText(fileId)) {
            FileAsset asset = fileAssetMapper.selectById(fileId);
            long actualSize = longValue(upload.get("Size"));
            if (asset == null || !asset.getUploadToken().equals(uploadToken) || actualSize != asset.getExpectedSize()) {
                return "pre-finish".equals(type) ? rejectedTusHook("上传完成校验失败") : Map.of();
            }
            if ("post-finish".equals(type) && "UPLOADING".equals(asset.getStatus())) {
                asset.setStatus("UPLOADED");
                asset.setActualSize(actualSize);
                asset.setUpdatedAt(LocalDateTime.now());
                fileAssetMapper.updateById(asset);
            }
        }
        return Map.of();
    }

    @Override
    public FileAsset findReadyChatFile(String url, Long ownerId, Long receiverId) {
        Matcher matcher = FILE_URL_PATTERN.matcher(url == null ? "" : url);
        if (!matcher.matches()) return null;
        FileAsset asset = fileAssetMapper.selectById(matcher.group(1));
        if (asset == null || !"READY".equals(asset.getStatus()) || !"CLEAN".equals(asset.getScanStatus())) return null;
        if (!ownerId.equals(asset.getOwnerId()) || !receiverId.equals(asset.getReceiverId())) return null;
        return asset;
    }

    @Override
    public FileAsset findReadyGroupFile(String url, Long ownerId, Long groupId) {
        Matcher matcher = FILE_URL_PATTERN.matcher(url == null ? "" : url);
        if (!matcher.matches()) return null;
        FileAsset asset = fileAssetMapper.selectById(matcher.group(1));
        if (asset == null || !"READY".equals(asset.getStatus()) || !"CLEAN".equals(asset.getScanStatus())) return null;
        if (!"GROUP".equals(asset.getPurpose()) || !ownerId.equals(asset.getOwnerId()) || !groupId.equals(asset.getReceiverId())) return null;
        return asset;
    }

    public FileAsset findAccessibleAsset(String fileId, String accessToken, Long userId) {
        FileAsset asset = fileAssetMapper.selectById(fileId);
        if (asset == null || !"READY".equals(asset.getStatus())) return null;
        boolean userMayRead = userId != null && (userId.equals(asset.getOwnerId()) || userId.equals(asset.getReceiverId()));
        // 群文件：任何群成员可读（receiverId 存的是群 id）
        if (!userMayRead && userId != null && "GROUP".equals(asset.getPurpose()) && asset.getReceiverId() != null) {
            userMayRead = isGroupMember(userId, asset.getReceiverId());
        }
        if (!userMayRead && !asset.getAccessToken().equals(accessToken)) return null;
        return asset;
    }

    public Path resolveStoredFile(FileAsset asset) {
        if (asset == null || !StringUtils.hasText(asset.getStorageKey())) return null;
        Path file = fileStorageDir().resolve(asset.getStorageKey()).normalize();
        return file.startsWith(fileStorageDir()) ? file : null;
    }

    /** 清理过期且未确认的上传任务与 tusd 临时数据，避免本机磁盘长期积压。 */
    @Scheduled(fixedDelayString = "${app.file.cleanup-interval-ms:3600000}", initialDelayString = "${app.file.cleanup-initial-delay-ms:300000}")
    public void expireIncompleteUploads() {
        List<FileAsset> expiredAssets = fileAssetMapper.selectList(new QueryWrapper<FileAsset>()
                .lt("expires_at", LocalDateTime.now())
                .in("status", "UPLOADING", "UPLOADED"));
        for (FileAsset asset : expiredAssets) {
            deleteTusdTemporaryFiles(asset.getId());
            asset.setStatus("EXPIRED");
            asset.setUpdatedAt(LocalDateTime.now());
            fileAssetMapper.updateById(asset);
        }
    }

    /** Requeue checksum tasks interrupted by a local restart or unexpected shutdown. */
    @Scheduled(fixedDelayString = "${app.file.processing-retry-interval-ms:30000}", initialDelayString = "${app.file.processing-retry-initial-delay-ms:3000}")
    public void resumeProcessingUploads() {
        List<FileAsset> processingAssets = fileAssetMapper.selectList(new QueryWrapper<FileAsset>()
                .eq("status", "PROCESSING"));
        for (FileAsset asset : processingAssets) {
            fileFinalizationService.finalizeFile(asset.getId());
        }
    }

    private FileAsset findOwnedAsset(Long ownerId, String fileId) {
        if (ownerId == null || !StringUtils.hasText(fileId)) return null;
        FileAsset asset = fileAssetMapper.selectById(fileId);
        return asset != null && ownerId.equals(asset.getOwnerId()) ? asset : null;
    }

    private boolean isFriend(Long ownerId, Long receiverId) {
        Long count = friendshipMapper.selectCount(new QueryWrapper<Friendship>()
                .eq("user_id", ownerId).eq("friend_id", receiverId).eq("status", 1));
        return count != null && count > 0;
    }

    private boolean isGroupMember(Long userId, Long groupId) {
        return groupService != null && groupService.isMember(groupId, userId);
    }

    private Map<String, Object> buildFileResponse(FileAsset asset) {
        Map<String, Object> data = new HashMap<>();
        data.put("fileId", asset.getId());
        data.put("status", asset.getStatus());
        data.put("fileName", asset.getOriginalName());
        data.put("fileSize", asset.getActualSize() != null ? asset.getActualSize() : asset.getExpectedSize());
        data.put("fileUrl", "/files/" + asset.getId() + "/content?access=" + asset.getAccessToken());
        data.put("fileType", asset.getContentType());
        return data;
    }

    private Map<String, Object> rejectedTusHook(String message) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        Map<String, Object> response = new HashMap<>();
        response.put("StatusCode", 400);
        response.put("Body", "{\"message\":\"" + message + "\"}");
        response.put("Header", headers);
        return Map.of("RejectUpload", true, "HTTPResponse", response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String... keys) {
        Object current = source;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?>)) return Map.of();
            current = ((Map<String, Object>) current).get(key);
        }
        return current instanceof Map<?, ?> ? (Map<String, Object>) current : Map.of();
    }

    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private long longValue(Object value) {
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return -1L; }
    }

    private Path tusdDataDir() { return resolveConfiguredPath(tusdUploadDir); }
    private Path fileStorageDir() { return resolveConfiguredPath(storageDir); }
    private Path resolveConfiguredPath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        return (path.isAbsolute() ? path : Paths.get(System.getProperty("user.dir")).resolve(path)).toAbsolutePath().normalize();
    }

    private void deleteTusdTemporaryFiles(String id) {
        try {
            Files.deleteIfExists(tusdDataDir().resolve(id));
            Files.deleteIfExists(tusdDataDir().resolve(id + ".info"));
        } catch (IOException ignored) { }
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder value = new StringBuilder();
            for (byte item : digest.digest()) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

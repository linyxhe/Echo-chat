package com.echo.file;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.FileAssetMapper;
import com.echo.mapper.MessageMapper;
import com.echo.mapper.PostMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.FileAsset;
import com.echo.pojo.Message;
import com.echo.pojo.Post;
import com.echo.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次性数据迁移：把 DB 中仍指向旧公开目录的 /upload/&lt;file&gt; 引用改写为受控 /files/{id}/content?access=...。
 *
 * <p>覆盖 {@code user.avatar_url}、{@code post.media_urls}、{@code message.file_url}（FILE）、
 * {@code message.content}（IMAGE 且 content 即为 URL）。同一物理文件在所有引用间复用同一 asset，
 * 文件从 upload/ 搬移到 upload/files/{id}。</p>
 *
 * <p>运行方式（独立实例，不影响 8088 主服务）：</p>
 * <pre>
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 --app.migrate-legacy-files=true"
 * </pre>
 * 完成后观察日志 "Legacy migration complete"，随后停止该实例。幂等：仅处理仍以 /upload/ 开头的引用。
 */
@Component
@ConditionalOnProperty(name = "app.migrate-legacy-files", havingValue = "true")
public class LegacyFileMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyFileMigrationRunner.class);

    private final FileAssetMapper fileAssetMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final MessageMapper messageMapper;

    @Value("${app.file.intent-ttl-minutes:60}")
    private long intentTtlMinutes;

    public LegacyFileMigrationRunner(FileAssetMapper fileAssetMapper, UserMapper userMapper,
                                     PostMapper postMapper, MessageMapper messageMapper) {
        this.fileAssetMapper = fileAssetMapper;
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public void run(String... args) {
        log.info("=== Legacy /upload migration starting ===");
        Map<String, FileAsset> assetByFilename = new HashMap<>();
        int rewritten = 0;

        List<User> users = userMapper.selectList(new QueryWrapper<User>().likeRight("avatar_url", "/upload/"));
        for (User u : users) {
            String newUrl = migrateUrl(u.getAvatarUrl(), u.getId(), null, "AVATAR", assetByFilename);
            if (newUrl != null) {
                User upd = new User();
                upd.setId(u.getId());
                upd.setAvatarUrl(newUrl);
                userMapper.updateById(upd);
                rewritten++;
            }
        }
        log.info("Migrated user.avatar_url: {} rows", users.size());

        List<Post> posts = postMapper.selectList(null);
        int postRows = 0;
        for (Post p : posts) {
            if (p.getMediaUrls() == null || p.getMediaUrls().isEmpty()) continue;
            List<String> newUrls = new ArrayList<>();
            boolean changed = false;
            for (String url : p.getMediaUrls()) {
                String nu = migrateUrl(url, p.getUserId(), null, "POST", assetByFilename);
                if (nu != null) {
                    newUrls.add(nu);
                    changed = true;
                } else {
                    newUrls.add(url);
                }
            }
            if (changed) {
                Post upd = new Post();
                upd.setId(p.getId());
                upd.setMediaUrls(newUrls);
                postMapper.updateById(upd);
                rewritten++;
                postRows++;
            }
        }
        log.info("Migrated post.media_urls: {} rows", postRows);

        List<Message> fileMsgs = messageMapper.selectList(new QueryWrapper<Message>().likeRight("file_url", "/upload/"));
        for (Message m : fileMsgs) {
            String newUrl = migrateUrl(m.getFileUrl(), m.getSenderId(), m.getReceiverId(), "CHAT", assetByFilename);
            if (newUrl != null) {
                Message upd = new Message();
                upd.setId(m.getId());
                upd.setFileUrl(newUrl);
                messageMapper.updateById(upd);
                rewritten++;
            }
        }
        log.info("Migrated message.file_url: {} rows", fileMsgs.size());

        List<Message> imgMsgs = messageMapper.selectList(new QueryWrapper<Message>()
                .eq("message_type", "IMAGE").likeRight("content", "/upload/"));
        for (Message m : imgMsgs) {
            String newUrl = migrateUrl(m.getContent(), m.getSenderId(), m.getReceiverId(), "CHAT", assetByFilename);
            if (newUrl != null) {
                Message upd = new Message();
                upd.setId(m.getId());
                upd.setContent(newUrl);
                messageMapper.updateById(upd);
                rewritten++;
            }
        }
        log.info("Migrated message.content(IMAGE): {} rows", imgMsgs.size());

        log.info("=== Legacy migration complete: {} references rewritten, {} physical files migrated ===",
                rewritten, assetByFilename.size());
    }

    /** 迁移单个 URL；同一物理文件复用同一 asset。返回 null 表示无需迁移或源文件缺失。 */
    private String migrateUrl(String url, Long ownerId, Long receiverId, String purpose, Map<String, FileAsset> assetByFilename) {
        if (url == null || !url.startsWith("/upload/")) return null;
        String filename = url.substring("/upload/".length());
        int query = filename.indexOf('?');
        if (query >= 0) filename = filename.substring(0, query);
        if (filename.isEmpty() || filename.contains("/") || filename.contains("\\")) return null;

        FileAsset asset = assetByFilename.get(filename);
        if (asset == null) {
            asset = createAsset(ownerId, receiverId, purpose, filename);
            if (asset == null) return null;
            assetByFilename.put(filename, asset);
        }
        return "/files/" + asset.getId() + "/content?access=" + asset.getAccessToken();
    }

    private FileAsset createAsset(Long ownerId, Long receiverId, String purpose, String filename) {
        Path source = legacyUploadDir().resolve(filename).normalize();
        if (!Files.isRegularFile(source)) {
            log.warn("Skipping missing legacy file: {}", source);
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        FileAsset asset = new FileAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setOwnerId(ownerId);
        asset.setReceiverId(receiverId != null && receiverId > 0 ? receiverId : null);
        asset.setPurpose(purpose);
        asset.setOriginalName(filename);
        asset.setContentType(guessContentType(filename));
        asset.setExpectedSize(sizeOrNull(source));
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
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            asset.setActualSize(Files.size(target));
            asset.setSha256(sha256(target));
            asset.setStorageKey(asset.getId());
            asset.setScanStatus("CLEAN");
            asset.setStatus("READY");
            asset.setUpdatedAt(LocalDateTime.now());
            fileAssetMapper.updateById(asset);
            return asset;
        } catch (IOException e) {
            log.error("Failed to migrate file {}", source, e);
            fileAssetMapper.deleteById(asset.getId());
            return null;
        }
    }

    private Path legacyUploadDir() {
        return Paths.get(System.getProperty("user.dir"), "upload").toAbsolutePath().normalize();
    }

    private Path fileStorageDir() {
        return Paths.get(System.getProperty("user.dir"), "upload", "files").toAbsolutePath().normalize();
    }

    private Long sizeOrNull(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return null;
        }
    }

    private String guessContentType(String filename) {
        try {
            String detected = Files.probeContentType(Paths.get(filename));
            return detected != null ? detected : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
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

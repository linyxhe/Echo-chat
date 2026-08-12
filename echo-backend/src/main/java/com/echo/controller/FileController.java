package com.echo.controller;

import com.echo.file.FileService;
import com.echo.file.FileServiceImpl;
import com.echo.file.dto.ChatUploadIntentRequest;
import com.echo.pojo.FileAsset;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;
    private final FileServiceImpl fileServiceImpl;
    private final UserService userService;

    public FileController(FileService fileService, FileServiceImpl fileServiceImpl, UserService userService) {
        this.fileService = fileService;
        this.fileServiceImpl = fileServiceImpl;
        this.userService = userService;
    }

    @PostMapping("/chat/upload-intents")
    public Result<Object> createChatUploadIntent(@RequestBody ChatUploadIntentRequest request) {
        Long currentUserId = getCurrentUserId();
        return currentUserId == null ? Result.fail("未登录") : fileService.createChatUploadIntent(currentUserId, request);
    }

    @PostMapping("/{fileId}/complete")
    public Result<Object> completeUpload(@PathVariable String fileId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId == null ? Result.fail("未登录") : fileService.completeUpload(currentUserId, fileId);
    }

    @GetMapping("/{fileId}/status")
    public Result<Object> getUploadStatus(@PathVariable String fileId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId == null ? Result.fail("未登录") : fileService.getUploadStatus(currentUserId, fileId);
    }

    @DeleteMapping("/{fileId}")
    public Result<Object> cancelUpload(@PathVariable String fileId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId == null ? Result.fail("未登录") : fileService.cancelUpload(currentUserId, fileId);
    }

    /**
     * 浏览器图片标签无法携带 JWT，因此允许随机 access 令牌访问；登录用户本人或接收者也可不带令牌读取。
     */
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> download(@PathVariable String fileId,
                                             @RequestParam(required = false) String access,
                                             @RequestParam(defaultValue = "false") boolean download) {
        FileAsset asset = fileServiceImpl.findAccessibleAsset(fileId, access, getCurrentUserId());
        Path file = fileServiceImpl.resolveStoredFile(asset);
        if (file == null || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detected = Files.probeContentType(file);
            if (StringUtils.hasText(detected)) mediaType = MediaType.parseMediaType(detected);
            else if (StringUtils.hasText(asset.getContentType())) mediaType = MediaType.parseMediaType(asset.getContentType());
        } catch (Exception ignored) { }

        ContentDisposition disposition = ContentDisposition.builder(download ? "attachment" : "inline")
                .filename(asset.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(file));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && StringUtils.hasText(auth.getName()) && !"anonymousUser".equals(auth.getName())) {
            com.echo.pojo.User user = userService.findByUsername(auth.getName());
            return user == null ? null : user.getId();
        }
        return null;
    }
}

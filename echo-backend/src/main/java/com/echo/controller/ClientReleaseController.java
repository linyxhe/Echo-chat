package com.echo.controller;

import com.echo.clientrelease.ClientReleaseService;
import com.echo.pojo.ClientRelease;
import com.echo.pojo.User;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/client-releases")
public class ClientReleaseController {

    private final ClientReleaseService clientReleaseService;
    private final UserService userService;

    public ClientReleaseController(ClientReleaseService clientReleaseService, UserService userService) {
        this.clientReleaseService = clientReleaseService;
        this.userService = userService;
    }

    @GetMapping("/public")
    public Result<Object> getPublicReleases() {
        return Result.success(clientReleaseService.listPublished());
    }

    @GetMapping("/admin")
    public Result<Object> getAllReleases() {
        return isAdmin() ? Result.success(clientReleaseService.listAll()) : Result.fail("无权限");
    }

    @PostMapping(value = "/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> uploadRelease(@RequestParam String platform,
                                        @RequestParam String version,
                                        @RequestParam(required = false) String releaseNotes,
                                        @RequestParam(defaultValue = "true") boolean publish,
                                        @RequestParam("file") MultipartFile file) {
        Long adminId = getAdminId();
        if (adminId == null) return Result.fail("无权限");
        try {
            return Result.success(clientReleaseService.upload(adminId, platform, version, releaseNotes, publish, file));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/admin/{releaseId}/publish")
    public Result<Object> publishRelease(@PathVariable Long releaseId) {
        if (!isAdmin()) return Result.fail("无权限");
        return clientReleaseService.publish(releaseId) ? Result.success() : Result.fail("安装包版本不存在");
    }

    @DeleteMapping("/admin/{releaseId}")
    public Result<Object> deleteRelease(@PathVariable Long releaseId) {
        if (!isAdmin()) return Result.fail("无权限");
        return clientReleaseService.delete(releaseId) ? Result.success() : Result.fail("安装包版本不存在");
    }

    @GetMapping("/{releaseId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long releaseId) {
        ClientRelease release = clientReleaseService.findPublished(releaseId);
        Path file = clientReleaseService.resolveFile(release);
        if (file == null || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(release.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(file));
    }

    private Long getAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !StringUtils.hasText(auth.getName())
                || "anonymousUser".equals(auth.getName())) return null;
        User user = userService.findByUsername(auth.getName());
        return user != null && "ADMIN".equals(user.getRole()) ? user.getId() : null;
    }

    private boolean isAdmin() {
        return getAdminId() != null;
    }
}

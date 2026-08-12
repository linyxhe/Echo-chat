package com.echo.controller;

import com.echo.pojo.KbDocument;
import com.echo.pojo.User;
import com.echo.service.KbIndexWorker;
import com.echo.service.KbService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库管理（管理端）。默认需登录；除 admin 角色外一律返回「无权限」（同 SystemConfigController 模式）。
 * 上传为异步索引：提交即返回，状态在 kb_document.status 流转（PENDING→INDEXING→READY/FAILED）。
 */
@RestController
@RequestMapping("/admin/kb")
public class KbController {

    @Autowired
    private KbService kbService;

    @Autowired
    private KbIndexWorker kbIndexWorker;

    @Autowired
    private UserService userService;

    @GetMapping("/documents")
    public Result<Object> list(@RequestParam(required = false) String keyword) {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(kbService.listDocuments(keyword));
    }

    @GetMapping("/documents/{id}")
    public Result<Object> detail(@PathVariable Long id) {
        if (!isAdmin()) return Result.fail("无权限");
        KbDocument doc = kbService.getDocument(id);
        return doc != null ? Result.success(doc) : Result.fail("文档不存在");
    }

    @GetMapping("/categories")
    public Result<Object> categories() {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(kbService.listAllCategories());
    }

    @PostMapping("/documents")
    public Result<Object> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(defaultValue = "true") boolean aiEnabled) {
        if (!isAdmin()) return Result.fail("无权限");
        if (file == null || file.isEmpty()) return Result.fail("文件为空");
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        try {
            byte[] bytes = file.getBytes();
            KbDocument doc = kbService.submitUpload(filename, file.getContentType(), category, aiEnabled, bytes, currentUserId());
            kbIndexWorker.indexDocument(doc.getId(), bytes, filename, file.getContentType());
            return Result.success("已提交索引，正在解析");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("读取文件失败：" + e.getMessage());
        }
    }

    @PutMapping("/documents/{id}")
    public Result<Object> update(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        if (!isAdmin()) return Result.fail("无权限");
        String category = body != null ? body.get("category") : null;
        Boolean aiEnabled = null;
        if (body != null && body.containsKey("aiEnabled")) {
            aiEnabled = Boolean.valueOf(String.valueOf(body.get("aiEnabled")));
        }
        return kbService.updateAccess(id, category, aiEnabled) ? Result.success("已更新") : Result.fail("文档不存在");
    }

    @DeleteMapping("/documents/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        if (!isAdmin()) return Result.fail("无权限");
        return kbService.deleteDocument(id) ? Result.success("已删除") : Result.fail("文档不存在");
    }

    @GetMapping("/stats")
    public Result<Object> stats() {
        if (!isAdmin()) return Result.fail("无权限");
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", kbService.isEnabled());
        m.put("documentCount", kbService.getDocumentCount());
        m.put("chunkCount", kbService.getChunkCount());
        m.put("modelName", kbService.getModelName());
        return Result.success(m);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            User user = userService.findByUsername(auth.getName());
            return user != null && "ADMIN".equals(user.getRole());
        }
        return false;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            User user = userService.findByUsername(auth.getName());
            return user == null ? null : user.getId();
        }
        return null;
    }
}

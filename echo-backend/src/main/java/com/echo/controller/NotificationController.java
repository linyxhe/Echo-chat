package com.echo.controller;

import com.echo.service.NotificationService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统通知（当前用户）。默认需登录。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<Object> list(@RequestParam(defaultValue = "20") Integer limit) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return Result.success(notificationService.list(uid, limit));
    }

    @GetMapping("/unread-count")
    public Result<Object> unreadCount() {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        Map<String, Object> data = new HashMap<>();
        data.put("unreadCount", notificationService.unreadCount(uid));
        return Result.success(data);
    }

    @PutMapping("/{id}/read")
    public Result<Object> read(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        notificationService.markRead(id, uid);
        return Result.success("已读");
    }

    @PutMapping("/read-all")
    public Result<Object> readAll() {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        notificationService.markAllRead(uid);
        return Result.success("已全部已读");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            com.echo.pojo.User u = userService.findByUsername(auth.getName());
            return u == null ? null : u.getId();
        }
        return null;
    }
}

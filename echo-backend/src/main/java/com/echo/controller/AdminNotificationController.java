package com.echo.controller;

import com.echo.service.NotificationService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端：系统通知全体广播（isAdmin 守卫）。
 */
@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @PostMapping("/broadcast")
    public Result<Object> broadcast(@RequestBody Map<String, String> body) {
        if (!isAdmin()) return Result.fail("无权限");
        String title = body.get("title");
        String content = body.get("content");
        if (title == null || content == null) return Result.fail("缺少 title/content");
        notificationService.notifyAll("SYSTEM", title, content);
        return Result.success("已广播");
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            com.echo.pojo.User user = userService.findByUsername(auth.getName());
            return user != null && "ADMIN".equals(user.getRole());
        }
        return false;
    }
}

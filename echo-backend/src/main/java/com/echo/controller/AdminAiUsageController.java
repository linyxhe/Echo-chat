package com.echo.controller;

import com.echo.pojo.User;
import com.echo.service.AiUsageLogService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-usage")
public class AdminAiUsageController {

    @Autowired
    private AiUsageLogService usageLogService;

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<Object> stats(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size) {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(usageLogService.stats(page, size));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return false;
        User user = userService.findByUsername(auth.getName());
        return user != null && "ADMIN".equals(user.getRole());
    }
}

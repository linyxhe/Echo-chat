package com.echo.controller;

import com.echo.pojo.User;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/profile")
    public Result<Object> getProfile() {
        String username = getCurrentUsername();
        User user = userService.findByUsername(username);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return userService.getProfile(user.getId());
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/profile")
    public Result<Object> updateProfile(@RequestBody User user) {
        String username = getCurrentUsername();
        User currentUser = userService.findByUsername(username);
        return userService.updateProfile(currentUser.getId(), user);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Object> updatePassword(@RequestBody Map<String, String> payload) {
        String username = getCurrentUsername();
        User currentUser = userService.findByUsername(username);
        
        String oldPassword = payload.get("oldPassword");
        String newPassword = payload.get("newPassword");
        
        return userService.updatePassword(currentUser.getId(), oldPassword, newPassword);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }
}

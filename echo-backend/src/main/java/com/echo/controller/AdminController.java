package com.echo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echo.mapper.ReportMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.Report;
import com.echo.pojo.User;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private ReportMapper reportMapper;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Result<Object> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        
        Result<Object> result = userService.login(username, password);
        if (result.getCode() == 200) {
            Map<String, Object> data = (Map<String, Object>) result.getData();
            User user = userMapper.selectById((Long) data.get("userId"));
            if (!"ADMIN".equals(user.getRole())) {
                return Result.fail("非管理员账号");
            }
        }
        return result;
    }

    /**
     * 获取用户列表
     */
    @GetMapping("/users")
    public Result<Object> getUsers(@RequestParam(required = false) String username,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size) {
        if (!isAdmin()) return Result.fail("无权限");
        
        Page<User> pageParam = new Page<>(page, size);
        QueryWrapper<User> query = new QueryWrapper<>();
        if (username != null) {
            query.like("username", username);
        }
        
        IPage<User> resultPage = userMapper.selectPage(pageParam, query);
        // 清除密码
        resultPage.getRecords().forEach(u -> u.setPasswordHash(null));
        
        return Result.success(resultPage);
    }

    /**
     * 封禁/解封用户
     */
    @PutMapping("/users/{userId}/status")
    public Result<Object> updateUserStatus(@PathVariable Long userId, @RequestBody Map<String, Integer> payload) {
        if (!isAdmin()) return Result.fail("无权限");
        
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");
        
        Integer status = payload.get("status");
        user.setStatus(status);
        userMapper.updateById(user);
        
        return Result.success("操作成功");
    }

    /**
     * 获取举报列表
     */
    @GetMapping("/reports")
    public Result<Object> getReports(@RequestParam(defaultValue = "PENDING") String status,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size) {
        if (!isAdmin()) return Result.fail("无权限");
        
        Page<Report> pageParam = new Page<>(page, size);
        QueryWrapper<Report> query = new QueryWrapper<>();
        query.eq("status", status);
        
        return Result.success(reportMapper.selectPage(pageParam, query));
    }

    /**
     * 处理举报
     */
    @PutMapping("/reports/{reportId}/handle")
    public Result<Object> handleReport(@PathVariable Long reportId, @RequestBody Map<String, String> payload) {
        if (!isAdmin()) return Result.fail("无权限");
        
        Report report = reportMapper.selectById(reportId);
        if (report == null) return Result.fail("举报不存在");
        
        String action = payload.get("action"); // PROCESS or DISMISS
        
        if ("PROCESS".equals(action)) {
            report.setStatus("PROCESSED");
        } else {
            report.setStatus("DISMISSED");
        }
        
        report.setAdminId(getCurrentUserId());
        report.setProcessedAt(LocalDateTime.now());
        
        reportMapper.updateById(report);
        
        return Result.success("处理成功");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
             return userService.findByUsername(auth.getName()).getId();
        }
        return null;
    }
    
    private boolean isAdmin() {
        Long userId = getCurrentUserId();
        if (userId == null) return false;
        User user = userMapper.selectById(userId);
        return user != null && "ADMIN".equals(user.getRole());
    }
}

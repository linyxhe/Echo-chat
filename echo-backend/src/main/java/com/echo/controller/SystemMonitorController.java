package com.echo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.PostMapper;
import com.echo.mapper.ReportMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.User;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/monitor")
public class SystemMonitorController {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PostMapper postMapper;
    
    @Autowired
    private ReportMapper reportMapper;
    
    @Autowired
    private UserService userService;

    @GetMapping("/stats")
    public Result<Object> getStats() {
        if (!isAdmin()) return Result.fail("无权限");
        
        Map<String, Object> stats = new HashMap<>();
        
        // 用户总数
        stats.put("totalUsers", userMapper.selectCount(null));
        
        // 今日新增用户
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.apply("DATE(created_at) = CURDATE()");
        stats.put("newUsersToday", userMapper.selectCount(userQuery));
        
        // 帖子总数
        stats.put("totalPosts", postMapper.selectCount(null));
        
        // 待处理举报
        QueryWrapper<com.echo.pojo.Report> reportQuery = new QueryWrapper<>();
        reportQuery.eq("status", "PENDING");
        stats.put("pendingReports", reportMapper.selectCount(reportQuery));
        
        return Result.success(stats);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            User user = userService.findByUsername(auth.getName());
            return user != null && "ADMIN".equals(user.getRole());
        }
        return false;
    }
}

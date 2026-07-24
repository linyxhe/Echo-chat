package com.echo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echo.mapper.ReportMapper;
import com.echo.pojo.Report;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/reports")
public class ReportController {

    @Autowired
    private ReportMapper reportMapper;
    
    @Autowired
    private UserService userService;

    @PostMapping
    public Result<Object> createReport(@RequestBody Report report) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        
        if (report.getReportType() == null || report.getReportedUserId() == null) {
            return Result.fail("举报目标不能为空");
        }
        
        report.setReporterId(currentUserId);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        
        reportMapper.insert(report);
        
        return Result.success("举报提交成功，我们将尽快处理");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            // 这里假设UserService有缓存优化，或者可以直接从Token解析
             return userService.findByUsername(auth.getName()).getId();
        }
        return null;
    }
}

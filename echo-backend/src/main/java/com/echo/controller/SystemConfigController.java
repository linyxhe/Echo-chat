package com.echo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.SystemConfigMapper;
import com.echo.pojo.SystemConfig;
import com.echo.pojo.User;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/system")
public class SystemConfigController {

    @Autowired
    private SystemConfigMapper systemConfigMapper;
    
    @Autowired
    private UserService userService;

    @Autowired
    private com.echo.service.WebSearchQuotaService webSearchQuotaService;

    @Autowired
    private com.echo.service.ExternalApiPolicyService externalApiPolicyService;

    @GetMapping("/configs")
    public Result<Object> getConfigs() {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(systemConfigMapper.selectList(null));
    }

    @PutMapping("/configs")
    public Result<Object> updateConfig(@RequestBody Map<String, String> payload) {
        if (!isAdmin()) return Result.fail("无权限");
        
        String key = payload.get("key");
        String value = payload.get("value");
        
        QueryWrapper<SystemConfig> query = new QueryWrapper<>();
        query.eq("config_key", key);
        SystemConfig config = systemConfigMapper.selectOne(query);
        
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.insert(config);
        } else {
            config.setConfigValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigMapper.updateById(config);
        }
        
        return Result.success("配置更新成功");
    }

    @GetMapping("/search-quota")
    public Result<Object> getSearchQuota() {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(webSearchQuotaService.status());
    }

    @PutMapping("/search-quota")
    public Result<Object> updateSearchQuota(@RequestBody Map<String, Object> payload) {
        if (!isAdmin()) return Result.fail("无权限");
        try {
            boolean enabled = payload != null && Boolean.TRUE.equals(payload.get("enabled"));
            int quota = payload == null || payload.get("monthlyQuota") == null ? 1000 : Integer.parseInt(String.valueOf(payload.get("monthlyQuota")));
            int percent = payload == null || payload.get("stopPercent") == null ? 90 : Integer.parseInt(String.valueOf(payload.get("stopPercent")));
            webSearchQuotaService.update(enabled, quota, percent);
            return Result.success(webSearchQuotaService.status());
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/weather-quota")
    public Result<Object> getWeatherQuota() {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(externalApiPolicyService.status("weather"));
    }

    @PutMapping("/weather-quota")
    public Result<Object> updateWeatherQuota(@RequestBody Map<String, Object> payload) {
        if (!isAdmin()) return Result.fail("无权限");
        try {
            boolean enabled = payload != null && Boolean.TRUE.equals(payload.get("enabled"));
            int quota = payload == null || payload.get("monthlyQuota") == null ? 1000 : Integer.parseInt(String.valueOf(payload.get("monthlyQuota")));
            int percent = payload == null || payload.get("stopPercent") == null ? 90 : Integer.parseInt(String.valueOf(payload.get("stopPercent")));
            String quotaPeriod = payload == null || payload.get("quotaPeriod") == null ? "DAILY" : String.valueOf(payload.get("quotaPeriod"));
            externalApiPolicyService.update("weather", enabled, quota, percent, quotaPeriod);
            return Result.success(externalApiPolicyService.status("weather"));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** Unified view for future external tools; each provider is policy-controlled server-side. */
    @GetMapping("/external-api-policies")
    public Result<Object> getExternalApiPolicies() {
        if (!isAdmin()) return Result.fail("无权限");
        return Result.success(externalApiPolicyService.allStatuses());
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

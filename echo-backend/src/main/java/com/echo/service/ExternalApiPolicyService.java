package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.AgentToolCallMapper;
import com.echo.mapper.SystemConfigMapper;
import com.echo.pojo.AgentToolCall;
import com.echo.pojo.SystemConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central runtime policy for every external API used by Agent tools.
 * Credentials stay in server configuration; admins control only availability
 * and quota protection through system_config.
 */
@Service
public class ExternalApiPolicyService {
    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "search", new Definition("search", "ai.search", "web_search_propose", 1_000, 90, "MONTHLY"),
            // QWeather's free plan is commonly 1,000 calls per day; admins can
            // switch this to MONTHLY when their subscription uses a monthly cap.
            "weather", new Definition("weather", "ai.weather", "weather_propose", 1_000, 100, "DAILY")
    );

    private final AgentToolCallMapper toolCallMapper;
    private final SystemConfigMapper configMapper;

    public ExternalApiPolicyService(AgentToolCallMapper toolCallMapper, SystemConfigMapper configMapper) {
        this.toolCallMapper = toolCallMapper;
        this.configMapper = configMapper;
    }

    public PolicyStatus check(String provider) {
        Definition definition = definition(provider);
        boolean enabled = readBoolean(definition.prefix() + ".enabled", true);
        int quota = readInt(definition.prefix() + ".monthly-quota", definition.defaultQuota(), 1, 10_000_000);
        String quotaPeriod = readPeriod(definition.prefix() + ".quota-period", definition.defaultPeriod());
        int stopPercent = readInt(definition.prefix() + ".stop-percent", definition.defaultStopPercent(), 1, 100);
        LocalDate today = LocalDate.now();
        LocalDate startDate = "DAILY".equals(quotaPeriod) ? today : today.withDayOfMonth(1);
        LocalDate endDate = "DAILY".equals(quotaPeriod) ? today.plusDays(1) : today.plusMonths(1).withDayOfMonth(1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atStartOfDay();
        long used = toolCallMapper.selectCount(new QueryWrapper<AgentToolCall>()
                .eq("tool_name", definition.toolName())
                .eq("status", "SUCCESS")
                .ge("created_at", start)
                .lt("created_at", end));
        long stopAt = Math.max(1L, (long) Math.ceil(quota * stopPercent / 100.0d));
        boolean available = enabled && used < stopAt;
        String periodLabel = "DAILY".equals(quotaPeriod) ? "今日" : "本月";
        String reason = !enabled ? "该外部服务已被管理员停用"
                : available ? null : "该外部服务" + periodLabel + "已达到管理员设置的额度保护线（" + stopPercent + "%），暂不再调用";
        return new PolicyStatus(definition.id(), enabled, quota, quotaPeriod, stopPercent, used, stopAt,
                available, reason, startDate.toString());
    }

    public Map<String, Object> status(String provider) {
        PolicyStatus status = check(provider);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", status.provider());
        result.put("enabled", status.enabled());
        result.put("monthlyQuota", status.monthlyQuota());
        result.put("quotaPeriod", status.quotaPeriod());
        result.put("stopPercent", status.stopPercent());
        result.put("used", status.used());
        result.put("stopAt", status.stopAt());
        result.put("available", status.available());
        result.put("reason", status.reason());
        result.put("period", status.period());
        // Keep the old field for clients that still render `month`.
        result.put("month", status.period());
        return result;
    }

    public Map<String, Object> allStatuses() {
        Map<String, Object> result = new LinkedHashMap<>();
        DEFINITIONS.keySet().stream().sorted().forEach(provider -> result.put(provider, status(provider)));
        return result;
    }

    public void update(String provider, boolean enabled, int monthlyQuota, int stopPercent) {
        update(provider, enabled, monthlyQuota, stopPercent, definition(provider).defaultPeriod());
    }

    public void update(String provider, boolean enabled, int monthlyQuota, int stopPercent, String quotaPeriod) {
        Definition definition = definition(provider);
        if (monthlyQuota < 1 || monthlyQuota > 10_000_000) throw new IllegalArgumentException("额度上限必须在 1 到 10000000 之间");
        if (stopPercent < 1 || stopPercent > 100) throw new IllegalArgumentException("停止使用比例必须在 1 到 100 之间");
        String normalizedPeriod = normalizePeriod(quotaPeriod, definition.defaultPeriod());
        save(definition.prefix() + ".enabled", Boolean.toString(enabled), "管理员是否启用外部服务：" + provider);
        save(definition.prefix() + ".monthly-quota", Integer.toString(monthlyQuota), "外部服务额度保护上限（次）：" + provider);
        save(definition.prefix() + ".quota-period", normalizedPeriod, "外部服务额度统计周期（DAILY/MONTHLY）:" + provider);
        save(definition.prefix() + ".stop-percent", Integer.toString(stopPercent), "达到外部服务月度额度的该百分比后停止：" + provider);
    }

    private Definition definition(String provider) {
        Definition definition = DEFINITIONS.get(provider);
        if (definition == null) throw new IllegalArgumentException("未注册的外部服务策略：" + provider);
        return definition;
    }

    private void save(String key, String value, String description) {
        SystemConfig config = configMapper.selectOne(new QueryWrapper<SystemConfig>().eq("config_key", key));
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);
        } else {
            config.setConfigValue(value);
            config.setDescription(description);
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(config);
        }
    }

    private boolean readBoolean(String key, boolean fallback) {
        String value = read(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : fallback;
    }

    private int readInt(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(read(key));
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String readPeriod(String key, String fallback) {
        return normalizePeriod(read(key), fallback);
    }

    private String normalizePeriod(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("DAILY".equals(normalized) || "MONTHLY".equals(normalized)) return normalized;
        return fallback;
    }

    private String read(String key) {
        SystemConfig config = configMapper.selectOne(new QueryWrapper<SystemConfig>().eq("config_key", key));
        return config == null ? null : config.getConfigValue();
    }

    private record Definition(String id, String prefix, String toolName, int defaultQuota, int defaultStopPercent,
                              String defaultPeriod) { }

    public record PolicyStatus(String provider, boolean enabled, int monthlyQuota, String quotaPeriod,
                               int stopPercent, long used, long stopAt, boolean available, String reason,
                               String period) { }
}

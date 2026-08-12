package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.SystemConfigMapper;
import com.echo.pojo.SystemConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** 基础内容审核：读取管理端维护的 sensitive.words 配置，返回命中的第一个词。 */
@Service
public class ContentModerationService {

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    public String findMatchedWord(String content) {
        if (!StringUtils.hasText(content)) return null;
        SystemConfig config = systemConfigMapper.selectOne(new QueryWrapper<SystemConfig>()
                .eq("config_key", "sensitive.words"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) return null;

        String normalized = content.toLowerCase(Locale.ROOT);
        for (String raw : config.getConfigValue().split(",")) {
            String word = raw == null ? "" : raw.trim();
            if (StringUtils.hasText(word) && normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return word;
            }
        }
        return null;
    }
}

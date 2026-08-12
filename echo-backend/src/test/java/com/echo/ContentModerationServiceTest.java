package com.echo;

import com.echo.mapper.SystemConfigMapper;
import com.echo.pojo.SystemConfig;
import com.echo.service.ContentModerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentModerationServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private ContentModerationService contentModerationService;

    @Test
    void blankContentDoesNotQuerySensitiveWords() {
        assertNull(contentModerationService.findMatchedWord("  "));
        verifyNoInteractions(systemConfigMapper);
    }

    @Test
    void matchesConfiguredWordIgnoringCaseAndWhitespace() {
        SystemConfig config = new SystemConfig();
        config.setConfigValue("  赌博, 暴力  ,诈骗");
        when(systemConfigMapper.selectOne(any())).thenReturn(config);

        assertEquals("赌博", contentModerationService.findMatchedWord("这是一条赌博信息"));
        assertEquals("暴力", contentModerationService.findMatchedWord("包含暴力内容"));
        verify(systemConfigMapper, org.mockito.Mockito.times(2)).selectOne(any());
    }

    @Test
    void returnsNullWhenConfigurationMissingOrNoWordMatches() {
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        assertNull(contentModerationService.findMatchedWord("普通内容"));

        SystemConfig config = new SystemConfig();
        config.setConfigValue("赌博,暴力");
        when(systemConfigMapper.selectOne(any())).thenReturn(config);
        assertNull(contentModerationService.findMatchedWord("今天天气很好"));
    }
}

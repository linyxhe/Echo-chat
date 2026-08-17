package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.echo.mapper.AiUsageLogMapper;
import com.echo.pojo.AiUsageLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiUsageLogService {

    private final AiUsageLogMapper mapper;

    @Autowired
    public AiUsageLogService(AiUsageLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long userId, Long assistantId, Long botUserId, String streamId,
                       String modelName, String status, int inputChars, int outputChars,
                       Long firstTokenMs, Long latencyMs, String errorMessage) {
        record(userId, assistantId, botUserId, streamId, modelName, status, inputChars, outputChars,
                firstTokenMs, latencyMs, errorMessage, null, null, null);
    }

    /** 记录调用审计；检索字段仅保存数量和最高分，不记录资料正文或用户问题。 */
    public void record(Long userId, Long assistantId, Long botUserId, String streamId,
                       String modelName, String status, int inputChars, int outputChars,
                       Long firstTokenMs, Long latencyMs, String errorMessage,
                       Integer kbPrivateHits, Integer kbPublicHits, Double kbMaxScore) {
        if (userId == null) return;
        try {
            AiUsageLog log = new AiUsageLog();
            log.setUserId(userId);
            log.setAssistantId(assistantId);
            log.setBotUserId(botUserId);
            log.setStreamId(streamId);
            log.setModelName(modelName);
            log.setStatus(status);
            log.setInputChars(Math.max(inputChars, 0));
            log.setOutputChars(Math.max(outputChars, 0));
            log.setFirstTokenMs(firstTokenMs);
            log.setLatencyMs(latencyMs);
            log.setErrorMessage(errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
            log.setKbPrivateHits(kbPrivateHits);
            log.setKbPublicHits(kbPublicHits);
            log.setKbMaxScore(kbMaxScore);
            log.setCreatedAt(LocalDateTime.now());
            mapper.insert(log);
        } catch (Exception ignored) {
            // 审计写入不能影响消息发送主链路。
        }
    }

    public Map<String, Object> stats(int recentLimit) {
        return stats(1, recentLimit);
    }

    /** Summary is global, while the recent audit rows are loaded page by page. */
    public Map<String, Object> stats(int page, int size) {
        Map<String, Object> result = new HashMap<>();
        QueryWrapper<AiUsageLog> aggregate = new QueryWrapper<>();
        aggregate.select(
                "COUNT(*) AS totalCalls",
                "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successCalls",
                "SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS errorCalls",
                "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledCalls",
                "AVG(first_token_ms) AS avgFirstTokenMs",
                "AVG(latency_ms) AS avgLatencyMs",
                "SUM(input_chars) AS inputChars",
                "SUM(output_chars) AS outputChars"
        );
        Map<String, Object> summary = mapper.selectMaps(aggregate).stream().findFirst().orElse(Map.of());
        result.put("summary", summary);
        int current = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        IPage<AiUsageLog> recentPage = mapper.selectPage(new Page<>(current, pageSize), new QueryWrapper<AiUsageLog>()
                .orderByDesc("created_at"));
        result.put("recent", recentPage.getRecords());
        result.put("page", recentPage.getCurrent());
        result.put("size", recentPage.getSize());
        result.put("total", recentPage.getTotal());
        result.put("pages", recentPage.getPages());
        return result;
    }
}

package com.echo.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.echo.agent.AgentConfirmationPayload;
import com.echo.agent.AgentExecutionContext;
import com.echo.mapper.AgentConfirmationMapper;
import com.echo.mapper.AgentDraftMapper;
import com.echo.mapper.AgentMemoryMapper;
import com.echo.mapper.MessageMapper;
import com.echo.pojo.AgentConfirmation;
import com.echo.pojo.AgentDraft;
import com.echo.pojo.AgentMemory;
import com.echo.pojo.Message;
import com.echo.websocket.ChatEndpoint;
import com.echo.websocket.pojo.ResultMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sole write gateway for Agent proposals. A model can create a short-lived pending item only;
 * the authenticated owner must later confirm it using the opaque token.
 */
@Service
public class AgentConfirmationService {
    public static final String MEMORY = "MEMORY";
    public static final String DRAFT = "DRAFT";
    public static final String WEATHER = "WEATHER";
    public static final String WEB_SEARCH = "WEB_SEARCH";
    public static final String REMINDER = "REMINDER";
    private static final DateTimeFormatter REMINDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CONFIRMATION_MINUTES = 15;

    private final AgentConfirmationMapper confirmationMapper;
    private final AgentMemoryMapper memoryMapper;
    private final AgentDraftMapper draftMapper;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final WeatherLookupService weatherLookupService;
    private final WebSearchService webSearchService;
    private final AgentReminderService reminderService;

    public AgentConfirmationService(AgentConfirmationMapper confirmationMapper,
                                    AgentMemoryMapper memoryMapper,
                                    AgentDraftMapper draftMapper,
                                    MessageMapper messageMapper,
                                    ConversationService conversationService,
                                    WeatherLookupService weatherLookupService,
                                    WebSearchService webSearchService,
                                    AgentReminderService reminderService) {
        this.confirmationMapper = confirmationMapper;
        this.memoryMapper = memoryMapper;
        this.draftMapper = draftMapper;
        this.messageMapper = messageMapper;
        this.conversationService = conversationService;
        this.weatherLookupService = weatherLookupService;
        this.webSearchService = webSearchService;
        this.reminderService = reminderService;
    }

    public AgentConfirmationPayload proposeMemory(AgentExecutionContext context, String content,
                                                  String reason, Integer expiresInDays) {
        String normalizedContent = normalize(content, 500);
        String normalizedReason = normalizeOptional(reason, 200);
        if (!StringUtils.hasText(normalizedContent)) throw new IllegalArgumentException("记忆内容不能为空，且不能超过 500 个字符");
        int days = expiresInDays == null ? 90 : Math.max(1, Math.min(expiresInDays, 365));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", normalizedContent);
        payload.put("reason", normalizedReason);
        payload.put("expiresInDays", days);
        return create(context, MEMORY, payload, "保存一条助手记忆（有效期 " + days + " 天）", normalizedContent);
    }

    public AgentConfirmationPayload proposeDraft(AgentExecutionContext context, String content, String title) {
        String normalizedContent = normalize(content, 2000);
        String normalizedTitle = normalizeOptional(title, 100);
        if (!StringUtils.hasText(normalizedContent)) throw new IllegalArgumentException("草稿内容不能为空，且不能超过 2000 个字符");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", normalizedContent);
        payload.put("title", normalizedTitle);
        return create(context, DRAFT, payload, normalizedTitle == null ? "保存一份消息草稿" : "保存草稿：" + normalizedTitle,
                normalizedContent);
    }

    public AgentConfirmationPayload proposeWeather(AgentExecutionContext context, String city, Integer days) {
        String normalizedCity = normalize(city, 80);
        if (!StringUtils.hasText(normalizedCity)) throw new IllegalArgumentException("城市名称不能为空，且不能超过 80 个字符");
        int forecastDays = days == null ? 1 : Math.max(1, Math.min(days, 7));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("city", normalizedCity);
        payload.put("days", forecastDays);
        return create(context, WEATHER, payload, "查询「" + normalizedCity + "」未来 " + forecastDays + " 天天气",
                "确认后仅会将城市名称发送给和风天气服务。");
    }

    public AgentConfirmationPayload proposeWebSearch(AgentExecutionContext context, String query) {
        String normalizedQuery = webSearchService.normalizeQuery(query);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", normalizedQuery);
        return create(context, WEB_SEARCH, payload, "联网搜索：「" + normalizedQuery + "」",
                "确认后仅会将这条搜索词发送给 Tavily 公开网页搜索服务。请勿搜索私人信息或凭据。");
    }

    public AgentConfirmationPayload proposeReminder(AgentExecutionContext context, String content, String remindAt) {
        String normalizedContent = normalize(content, 500);
        if (!StringUtils.hasText(normalizedContent)) throw new IllegalArgumentException("提醒内容不能为空，且不能超过 500 个字符");
        LocalDateTime scheduledAt = parseReminderTime(remindAt);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", normalizedContent);
        payload.put("scheduledAt", scheduledAt.format(REMINDER_TIME_FORMAT));
        return create(context, REMINDER, payload, "创建提醒：「" + normalizedContent + "」",
                "将在 " + scheduledAt.format(REMINDER_TIME_FORMAT) + " 通过站内通知提醒你。");
    }

    private AgentConfirmationPayload create(AgentExecutionContext context, String actionType,
                                            Map<String, Object> payload, String summary, String preview) {
        if (context == null || context.userId() == null || context.botUserId() == null || !StringUtils.hasText(context.streamId())) {
            throw new IllegalArgumentException("当前会话上下文无效，无法创建确认项");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(CONFIRMATION_MINUTES);
        AgentConfirmation confirmation = new AgentConfirmation();
        confirmation.setToken(newToken());
        confirmation.setUserId(context.userId());
        confirmation.setAssistantId(context.assistantId());
        confirmation.setBotUserId(context.botUserId());
        confirmation.setStreamId(context.streamId());
        confirmation.setActionType(actionType);
        confirmation.setPayload(JSON.toJSONString(payload));
        confirmation.setSummary(limit(summary, 500));
        confirmation.setStatus("PENDING");
        confirmation.setExpiresAt(expiresAt);
        confirmation.setCreatedAt(now);
        confirmationMapper.insert(confirmation);
        return new AgentConfirmationPayload(confirmation.getToken(), actionType, confirmation.getSummary(),
                limit(preview, 500), expiresAt, context.botUserId());
    }

    public List<AgentConfirmationPayload> listPending(Long userId) {
        if (userId == null) return List.of();
        LocalDateTime now = LocalDateTime.now();
        confirmationMapper.update(null, new UpdateWrapper<AgentConfirmation>()
                .eq("user_id", userId).eq("status", "PENDING").lt("expires_at", now).set("status", "EXPIRED"));
        List<AgentConfirmation> confirmations = confirmationMapper.selectList(new QueryWrapper<AgentConfirmation>()
                .eq("user_id", userId).eq("status", "PENDING").gt("expires_at", now).orderByAsc("created_at"));
        List<AgentConfirmationPayload> results = new ArrayList<>();
        for (AgentConfirmation confirmation : confirmations) {
            Map<String, Object> payload = payload(confirmation);
            results.add(new AgentConfirmationPayload(confirmation.getToken(), confirmation.getActionType(),
                    confirmation.getSummary(), preview(confirmation.getActionType(), payload),
                    confirmation.getExpiresAt(), confirmation.getBotUserId()));
        }
        return results;
    }

    @Transactional
    public Map<String, Object> confirm(Long userId, String token) {
        AgentConfirmation confirmation = pendingForOwner(userId, token);
        if (confirmation == null) throw new IllegalArgumentException("确认项不存在、已处理或已过期");
        LocalDateTime now = LocalDateTime.now();
        int claimed = confirmationMapper.update(null, new UpdateWrapper<AgentConfirmation>()
                .eq("id", confirmation.getId()).eq("user_id", userId).eq("status", "PENDING")
                .gt("expires_at", now).set("status", "CONFIRMED").set("confirmed_at", now));
        if (claimed != 1) throw new IllegalArgumentException("确认项已处理或已过期");
        Map<String, Object> payload = payload(confirmation);
        if (MEMORY.equals(confirmation.getActionType())) return saveMemory(confirmation, payload, now);
        if (DRAFT.equals(confirmation.getActionType())) return saveDraft(confirmation, payload, now);
        if (WEATHER.equals(confirmation.getActionType())) return queryWeather(confirmation, payload, now);
        if (WEB_SEARCH.equals(confirmation.getActionType())) return queryWeb(confirmation, payload, now);
        if (REMINDER.equals(confirmation.getActionType())) return createReminder(confirmation, payload, now);
        throw new IllegalArgumentException("不支持的确认操作");
    }

    public boolean reject(Long userId, String token) {
        if (userId == null || !StringUtils.hasText(token)) return false;
        return confirmationMapper.update(null, new UpdateWrapper<AgentConfirmation>()
                .eq("user_id", userId).eq("token", token.trim()).eq("status", "PENDING")
                .set("status", "REJECTED").set("confirmed_at", LocalDateTime.now())) == 1;
    }

    public List<Map<String, Object>> listMemories(Long userId) {
        if (userId == null) return List.of();
        LocalDateTime now = LocalDateTime.now();
        memoryMapper.delete(new QueryWrapper<AgentMemory>().eq("user_id", userId).lt("expires_at", now));
        return memoryMapper.selectList(new QueryWrapper<AgentMemory>().eq("user_id", userId)
                        .orderByDesc("created_at").last("LIMIT 100"))
                .stream().map(memory -> Map.<String, Object>of(
                        "id", memory.getId(), "assistantId", memory.getAssistantId() == null ? 0L : memory.getAssistantId(),
                        "content", memory.getContent(), "reason", memory.getReason() == null ? "" : memory.getReason(),
                        "expiresAt", memory.getExpiresAt() == null ? "" : memory.getExpiresAt().toString(),
                        "createdAt", memory.getCreatedAt() == null ? "" : memory.getCreatedAt().toString()))
                .toList();
    }

    public List<Map<String, Object>> listDrafts(Long userId) {
        if (userId == null) return List.of();
        return draftMapper.selectList(new QueryWrapper<AgentDraft>().eq("user_id", userId)
                        .orderByDesc("created_at").last("LIMIT 100"))
                .stream().map(draft -> Map.<String, Object>of(
                        "id", draft.getId(), "assistantId", draft.getAssistantId() == null ? 0L : draft.getAssistantId(),
                        "content", draft.getContent(), "title", draft.getTitle() == null ? "" : draft.getTitle(),
                        "createdAt", draft.getCreatedAt() == null ? "" : draft.getCreatedAt().toString()))
                .toList();
    }

    public boolean deleteMemory(Long userId, Long memoryId) {
        return userId != null && memoryId != null && memoryMapper.delete(new QueryWrapper<AgentMemory>()
                .eq("id", memoryId).eq("user_id", userId)) == 1;
    }

    public boolean deleteDraft(Long userId, Long draftId) {
        return userId != null && draftId != null && draftMapper.delete(new QueryWrapper<AgentDraft>()
                .eq("id", draftId).eq("user_id", userId)) == 1;
    }

    /** Only confirmed, non-expired memory for this exact assistant is eligible for a prompt. */
    public List<String> activeMemoryContents(Long userId, Long assistantId) {
        if (userId == null) return List.of();
        QueryWrapper<AgentMemory> query = new QueryWrapper<AgentMemory>().eq("user_id", userId)
                .and(wrapper -> wrapper.isNull("expires_at").or().gt("expires_at", LocalDateTime.now()))
                .orderByDesc("created_at").last("LIMIT 20");
        if (assistantId == null) query.isNull("assistant_id");
        else query.eq("assistant_id", assistantId);
        return memoryMapper.selectList(query).stream().map(AgentMemory::getContent)
                .filter(StringUtils::hasText).map(content -> limit(content, 500)).toList();
    }

    private Map<String, Object> saveMemory(AgentConfirmation confirmation, Map<String, Object> payload, LocalDateTime now) {
        String content = string(payload.get("content"));
        String reason = normalizeOptional(string(payload.get("reason")), 200);
        int days = number(payload.get("expiresInDays"), 90, 1, 365);
        if (!StringUtils.hasText(content) || content.length() > 500) throw new IllegalArgumentException("确认内容已失效");
        AgentMemory memory = new AgentMemory();
        memory.setUserId(confirmation.getUserId());
        memory.setAssistantId(confirmation.getAssistantId());
        memory.setContent(content);
        memory.setReason(reason);
        memory.setExpiresAt(now.plusDays(days));
        memory.setCreatedAt(now);
        memoryMapper.insert(memory);
        return Map.of("actionType", MEMORY, "memoryId", memory.getId(), "content", memory.getContent(),
                "expiresAt", memory.getExpiresAt().toString());
    }

    private Map<String, Object> saveDraft(AgentConfirmation confirmation, Map<String, Object> payload, LocalDateTime now) {
        String content = string(payload.get("content"));
        String title = normalizeOptional(string(payload.get("title")), 100);
        if (!StringUtils.hasText(content) || content.length() > 2000) throw new IllegalArgumentException("确认内容已失效");
        AgentDraft draft = new AgentDraft();
        draft.setUserId(confirmation.getUserId());
        draft.setAssistantId(confirmation.getAssistantId());
        draft.setContent(content);
        draft.setTitle(title);
        draft.setSourceConfirmationId(confirmation.getId());
        draft.setCreatedAt(now);
        draftMapper.insert(draft);
        return Map.of("actionType", DRAFT, "draftId", draft.getId(), "content", draft.getContent(),
                "title", draft.getTitle() == null ? "" : draft.getTitle());
    }

    private Map<String, Object> queryWeather(AgentConfirmation confirmation, Map<String, Object> payload, LocalDateTime now) {
        String city = normalize(string(payload.get("city")), 80);
        int days = number(payload.get("days"), 1, 1, 7);
        if (!StringUtils.hasText(city)) throw new IllegalArgumentException("确认内容已失效");
        WeatherLookupService.WeatherResult result = weatherLookupService.lookup(city, days);
        String answer = weatherSummary(result, days);
        Message message = new Message();
        message.setSenderId(confirmation.getBotUserId());
        message.setReceiverId(confirmation.getUserId());
        message.setClientMessageId("agent-weather:" + confirmation.getId());
        message.setMessageType("TEXT");
        message.setContent(answer);
        message.setAiSources(JSON.toJSONString(List.of(Map.of(
                "filename", "和风天气", "category", "外部天气", "url", result.sourceUrl(), "privateDocument", false))));
        message.setIsRead(false);
        message.setDeletedBySender(false);
        message.setDeletedByReceiver(false);
        message.setCreatedAt(now);
        messageMapper.insert(message);
        conversationService.updateOnSend(message.getSenderId(), message.getReceiverId(), message.getId(), now);
        pushWeatherMessage(message);
        return Map.of("actionType", WEATHER, "messageId", message.getId(), "content", answer);
    }

    private Map<String, Object> queryWeb(AgentConfirmation confirmation, Map<String, Object> payload, LocalDateTime now) {
        String query = webSearchService.normalizeQuery(string(payload.get("query")));
        WebSearchService.SearchResult result = webSearchService.search(query);
        String answer = searchSummary(result);
        Message message = new Message();
        message.setSenderId(confirmation.getBotUserId());
        message.setReceiverId(confirmation.getUserId());
        message.setClientMessageId("agent-web-search:" + confirmation.getId());
        message.setMessageType("TEXT");
        message.setContent(answer);
        List<Map<String, Object>> sources = result.items().stream().map(item -> Map.<String, Object>of(
                "filename", item.title(), "category", "外部网页搜索", "url", item.url(), "privateDocument", false)).toList();
        message.setAiSources(JSON.toJSONString(sources));
        message.setIsRead(false);
        message.setDeletedBySender(false);
        message.setDeletedByReceiver(false);
        message.setCreatedAt(now);
        messageMapper.insert(message);
        conversationService.updateOnSend(message.getSenderId(), message.getReceiverId(), message.getId(), now);
        pushWeatherMessage(message);
        return Map.of("actionType", WEB_SEARCH, "messageId", message.getId(), "content", answer);
    }

    private Map<String, Object> createReminder(AgentConfirmation confirmation, Map<String, Object> payload, LocalDateTime now) {
        String content = normalize(string(payload.get("content")), 500);
        LocalDateTime scheduledAt = parseReminderTime(string(payload.get("scheduledAt")));
        if (!StringUtils.hasText(content)) throw new IllegalArgumentException("确认内容已失效");
        com.echo.pojo.AgentReminder reminder = reminderService.create(confirmation, content, scheduledAt, now);
        return Map.of("actionType", REMINDER, "reminderId", reminder.getId(), "content", content,
                "scheduledAt", scheduledAt.format(REMINDER_TIME_FORMAT));
    }

    private void pushWeatherMessage(Message message) {
        ResultMessage event = new ResultMessage();
        event.setType("NEW_MESSAGE");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", message.getId());
        data.put("senderId", message.getSenderId());
        data.put("receiverId", message.getReceiverId());
        data.put("content", message.getContent());
        data.put("messageType", message.getMessageType());
        data.put("createdAt", message.getCreatedAt().toString());
        data.put("isRead", false);
        data.put("aiSources", message.getAiSources());
        event.setData(data);
        event.setTimestamp(System.currentTimeMillis());
        ChatEndpoint.sendToUser(message.getReceiverId(), event);
    }

    private String weatherSummary(WeatherLookupService.WeatherResult result, int days) {
        String place = result.city() + (StringUtils.hasText(result.admin()) ? "·" + result.admin() : "")
                + (StringUtils.hasText(result.country()) ? "（" + result.country() + "）" : "");
        StringBuilder answer = new StringBuilder("**" + place + "天气**\n\n");
        if (!result.now().isEmpty()) {
            answer.append("当前：").append(value(result.now(), "text", "天气情况未知"))
                    .append("，").append(value(result.now(), "temp", "—")).append("°C（体感 ")
                    .append(value(result.now(), "feelsLike", "—")).append("°C），")
                    .append(value(result.now(), "windDir", "风向未知")).append(" ")
                    .append(value(result.now(), "windSpeed", "—")).append(" km/h。\n\n");
        }
        int count = Math.min(days, result.daily().size());
        if (count > 0) answer.append("预报：\n");
        for (int index = 0; index < count; index++) {
            Map<String, Object> daily = result.daily().get(index);
            answer.append("- ").append(value(daily, "fxDate", "日期未知")).append("：")
                    .append(value(daily, "textDay", "天气情况未知")).append("，")
                    .append(value(daily, "tempMax", "—")).append(" / ").append(value(daily, "tempMin", "—")).append("°C")
                    .append("，降水量 ").append(value(daily, "precip", "—")).append(" mm\n");
        }
        answer.append("\n数据来源：[和风天气](").append(result.sourceUrl()).append(")（天气预报仅供参考）");
        return answer.toString();
    }

    private String value(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        return value instanceof String text && StringUtils.hasText(text) ? text : fallback;
    }

    private String searchSummary(WebSearchService.SearchResult result) {
        StringBuilder answer = new StringBuilder("**联网搜索结果**\n\n搜索词：").append(result.query()).append("\n\n");
        if (result.items().isEmpty()) {
            answer.append("没有获得可用的 HTTPS 网页结果。你可以换一个更具体的关键词后重试。\n");
        } else {
            for (int index = 0; index < result.items().size(); index++) {
                WebSearchService.SearchItem item = result.items().get(index);
                answer.append(index + 1).append(". [").append(item.title()).append("](").append(item.url()).append(")\n");
                if (StringUtils.hasText(item.content())) answer.append("   ").append(item.content()).append("\n");
            }
        }
        answer.append("\n来源由 [Tavily Search](").append(result.providerDocsUrl()).append(") 提供；请打开原网页核对重要信息。");
        return answer.toString();
    }

    private AgentConfirmation pendingForOwner(Long userId, String token) {
        if (userId == null || !StringUtils.hasText(token)) return null;
        return confirmationMapper.selectOne(new QueryWrapper<AgentConfirmation>()
                .eq("user_id", userId).eq("token", token.trim()).eq("status", "PENDING")
                .gt("expires_at", LocalDateTime.now()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(AgentConfirmation confirmation) {
        try {
            Object parsed = JSON.parse(confirmation.getPayload());
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) if (entry.getKey() instanceof String key) copy.put(key, entry.getValue());
                return copy;
            }
        } catch (Exception ignored) { }
        throw new IllegalArgumentException("确认内容已失效");
    }

    private String preview(String actionType, Map<String, Object> payload) {
        if (WEATHER.equals(actionType)) return "确认后仅会将城市名称「" + limit(string(payload.get("city")), 80) + "」发送给和风天气服务。";
        if (WEB_SEARCH.equals(actionType)) return "确认后仅会将搜索词「" + limit(string(payload.get("query")), 400) + "」发送给 Tavily 公开网页搜索服务。";
        if (REMINDER.equals(actionType)) return "将在 " + limit(string(payload.get("scheduledAt")), 32) + " 通过站内通知提醒你："
                + limit(string(payload.get("content")), 500);
        return limit(string(payload.get("content")), 500);
    }
    private String newToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String normalize(String value, int max) { return StringUtils.hasText(value) && value.trim().length() <= max ? value.trim() : null; }
    private String normalizeOptional(String value, int max) { return StringUtils.hasText(value) ? normalize(value, max) : null; }
    private String string(Object value) { return value instanceof String text ? text.trim() : ""; }
    private int number(Object value, int fallback, int min, int max) { return value instanceof Number number ? Math.max(min, Math.min(number.intValue(), max)) : fallback; }
    private LocalDateTime parseReminderTime(String value) {
        try {
            LocalDateTime scheduledAt = LocalDateTime.parse(value == null ? "" : value.trim(), REMINDER_TIME_FORMAT);
            LocalDateTime now = LocalDateTime.now();
            if (!scheduledAt.isAfter(now.plusMinutes(1))) throw new IllegalArgumentException("提醒时间至少应在 2 分钟后");
            if (scheduledAt.isAfter(now.plusDays(365))) throw new IllegalArgumentException("提醒时间不能超过一年后");
            return scheduledAt;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("提醒时间必须是 YYYY-MM-DD HH:mm 格式");
        }
    }
    private String limit(String value, int length) { return value == null ? "" : value.substring(0, Math.min(value.length(), length)); }
}

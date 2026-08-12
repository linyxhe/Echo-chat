package com.echo.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.ai.BotUserService;
import com.echo.mapper.AiAssistantMapper;
import com.echo.mapper.ConversationMapper;
import com.echo.mapper.MessageMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.AiAssistant;
import com.echo.pojo.Conversation;
import com.echo.pojo.Message;
import com.echo.pojo.User;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 用户自定义 AI 助手：保存角色设定，并为每个助手绑定一个 BOT 用户以复用现有消息链路。 */
@Service
public class AiAssistantService {

    private static final Map<String, String> DEFAULT_PERSONAS = Map.of(
            "GENERAL", "你是一个友好、可靠的 AI 助手，请用简体中文清晰回答。",
            "STUDY", "你是一名耐心的学习辅导老师，请分步骤讲解并主动检查理解情况。",
            "CODING", "你是一名严谨的编程助手，请优先给出可执行方案、边界条件和示例代码。",
            "WRITING", "你是一名写作助手，请根据用户目标优化结构、表达和语气。",
            "COMPANION", "你是一名温和的陪伴助手，请自然、尊重地回应用户，不做武断判断。"
    );

    private final AiAssistantMapper assistantMapper;
    private final UserMapper userMapper;
    private final BotUserService botUserService;
    private final KbService kbService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Autowired
    public AiAssistantService(AiAssistantMapper assistantMapper,
                              UserMapper userMapper,
                              BotUserService botUserService,
                              KbService kbService,
                              ConversationMapper conversationMapper,
                              MessageMapper messageMapper) {
        this.assistantMapper = assistantMapper;
        this.userMapper = userMapper;
        this.botUserService = botUserService;
        this.kbService = kbService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public List<Map<String, Object>> listMine(Long ownerId) {
        if (ownerId == null) return List.of();
        return assistantMapper.selectList(new QueryWrapper<AiAssistant>()
                        .eq("owner_id", ownerId)
                        .orderByDesc("updated_at"))
                .stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional
    public Result<Object> create(Long ownerId, String name, String assistantType,
                                 String persona, String knowledgeCategory, String defaultOperations) {
        List<String> categories = StringUtils.hasText(knowledgeCategory)
                ? List.of(knowledgeCategory) : List.of();
        return create(ownerId, name, assistantType, persona, categories, defaultOperations);
    }

    @Transactional
    public Result<Object> create(Long ownerId, String name, String assistantType,
                                 String persona, List<String> knowledgeCategories, String defaultOperations) {
        if (ownerId == null) return Result.fail("未登录");
        if (!StringUtils.hasText(name)) return Result.fail("请输入 AI 助手名称");
        String normalizedName = name.trim();
        if (normalizedName.length() > 50) return Result.fail("AI 助手名称不能超过 50 个字符");

        String type = StringUtils.hasText(assistantType)
                ? assistantType.trim().toUpperCase(Locale.ROOT) : "GENERAL";
        if (!DEFAULT_PERSONAS.containsKey(type)) return Result.fail("AI 助手类型无效");

        String normalizedPersona = StringUtils.hasText(persona) ? persona.trim() : DEFAULT_PERSONAS.get(type);
        if (normalizedPersona.length() > 4000) return Result.fail("角色设定不能超过 4000 个字符");
        LinkedHashSet<String> categorySet = new LinkedHashSet<>();
        if (knowledgeCategories != null) {
            for (String item : knowledgeCategories) {
                if (StringUtils.hasText(item)) categorySet.add(item.trim());
            }
        }
        if (categorySet.size() > 20) return Result.fail("知识库范围最多选择 20 个分类");
        if (categorySet.stream().anyMatch(item -> item.length() > 100)) {
            return Result.fail("知识库分类不能超过 100 个字符");
        }
        List<String> availableCategories = kbService.listCategories();
        if (categorySet.stream().anyMatch(selected -> availableCategories.stream()
                .noneMatch(item -> item.equalsIgnoreCase(selected)))) {
            return Result.fail("知识库分类不存在、未启用或尚未完成索引");
        }
        String operations = StringUtils.hasText(defaultOperations) ? defaultOperations.trim() : null;
        if (operations != null && operations.length() > 4000) return Result.fail("默认操作不能超过 4000 个字符");

        String suffix = UUID.randomUUID().toString().replace("-", "");
        User bot = new User();
        bot.setUsername("ai_user_" + suffix);
        bot.setNickname(normalizedName);
        bot.setPasswordHash("ai-bot-" + suffix);
        bot.setEmail("ai-" + suffix + "@echo.local");
        bot.setEmailVerified(true);
        bot.setStatus(1);
        bot.setRole("BOT");
        userMapper.insert(bot);

        LocalDateTime now = LocalDateTime.now();
        AiAssistant assistant = new AiAssistant();
        assistant.setOwnerId(ownerId);
        assistant.setBotUserId(bot.getId());
        assistant.setName(normalizedName);
        assistant.setAssistantType(type);
        assistant.setPersona(normalizedPersona);
        List<String> categories = new ArrayList<>(categorySet);
        assistant.setKnowledgeCategory(categories.size() == 1 ? categories.get(0) : null);
        assistant.setKnowledgeCategories(categories.isEmpty() ? null : JSON.toJSONString(categories));
        assistant.setDefaultOperations(operations);
        assistant.setStatus("ACTIVE");
        assistant.setCreatedAt(now);
        assistant.setUpdatedAt(now);
        assistantMapper.insert(assistant);
        return Result.success(toView(assistant));
    }

    public AiAssistant findOwnedByBotUser(Long ownerId, Long botUserId) {
        if (ownerId == null || botUserId == null) return null;
        return assistantMapper.selectOne(new QueryWrapper<AiAssistant>()
                .eq("owner_id", ownerId)
                .eq("bot_user_id", botUserId)
                .eq("status", "ACTIVE"));
    }

    public AiAssistant findOwnedById(Long ownerId, Long assistantId) {
        if (ownerId == null || assistantId == null) return null;
        return assistantMapper.selectOne(new QueryWrapper<AiAssistant>()
                .eq("id", assistantId)
                .eq("owner_id", ownerId)
                .eq("status", "ACTIVE"));
    }

    /**
     * 彻底删除用户自建助手：删除助手配置、BOT 用户、双向会话、聊天消息以及私有知识库文档/分片。
     * 系统 AI 不经过此方法，因此不会被误删。
     */
    @Transactional
    public boolean deleteOwned(Long ownerId, Long assistantId) {
        AiAssistant assistant = findOwnedById(ownerId, assistantId);
        if (assistant == null) return false;

        List<com.echo.pojo.KbDocument> documents = kbService.listPrivateDocuments(ownerId, assistantId);
        for (com.echo.pojo.KbDocument document : documents) {
            kbService.deletePrivateDocument(ownerId, assistantId, document.getId());
        }

        Long botUserId = assistant.getBotUserId();
        if (botUserId != null) {
            messageMapper.delete(new QueryWrapper<Message>().and(wrapper -> wrapper
                    .nested(nested -> nested.eq("sender_id", ownerId).eq("receiver_id", botUserId))
                    .or()
                    .nested(nested -> nested.eq("sender_id", botUserId).eq("receiver_id", ownerId))));
            conversationMapper.delete(new QueryWrapper<Conversation>().and(wrapper -> wrapper
                    .nested(nested -> nested.eq("user1_id", ownerId).eq("user2_id", botUserId))
                    .or()
                    .nested(nested -> nested.eq("user1_id", botUserId).eq("user2_id", ownerId))));
            userMapper.deleteById(botUserId);
        }
        return assistantMapper.deleteById(assistant.getId()) > 0;
    }

    @Transactional
    public Result<Object> updateRemark(Long ownerId, Long assistantId, String remark) {
        AiAssistant assistant = findOwnedById(ownerId, assistantId);
        if (assistant == null) return Result.fail("AI 助手不存在或无权修改");
        String normalized = StringUtils.hasText(remark) ? remark.trim() : null;
        if (normalized != null && normalized.length() > 50) return Result.fail("备注不能超过 50 个字符");
        assistant.setRemark(normalized);
        assistant.setUpdatedAt(LocalDateTime.now());
        assistantMapper.updateById(assistant);
        return Result.success(toView(assistant));
    }

    public AiAssistant findByBotUser(Long botUserId) {
        if (botUserId == null) return null;
        return assistantMapper.selectOne(new QueryWrapper<AiAssistant>()
                .eq("bot_user_id", botUserId)
                .eq("status", "ACTIVE"));
    }

    /** 系统助手对所有用户开放；用户创建的助手只允许创建者访问。 */
    public boolean canChat(Long ownerId, Long botUserId) {
        if (ownerId == null || botUserId == null) return false;
        if (botUserId.equals(botUserService.getBotUserId())) return true;
        return findOwnedByBotUser(ownerId, botUserId) != null;
    }

    public List<String> listKnowledgeCategories() {
        return kbService.listCategories();
    }

    public List<String> getKnowledgeCategories(AiAssistant assistant) {
        if (assistant == null) return List.of();
        if (StringUtils.hasText(assistant.getKnowledgeCategories())) {
            try {
                List<String> list = JSON.parseArray(assistant.getKnowledgeCategories(), String.class);
                return list == null ? List.of() : list;
            } catch (Exception ignored) {
                // 兼容历史数据，回退到单分类字段。
            }
        }
        return StringUtils.hasText(assistant.getKnowledgeCategory())
                ? List.of(assistant.getKnowledgeCategory()) : List.of();
    }

    private Map<String, Object> toView(AiAssistant assistant) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", assistant.getId());
        map.put("botUserId", assistant.getBotUserId());
        map.put("name", assistant.getName());
        map.put("remark", assistant.getRemark());
        map.put("assistantType", assistant.getAssistantType());
        map.put("persona", assistant.getPersona());
        map.put("knowledgeCategory", assistant.getKnowledgeCategory());
        map.put("knowledgeCategories", getKnowledgeCategories(assistant));
        map.put("defaultOperations", assistant.getDefaultOperations());
        map.put("status", assistant.getStatus());
        map.put("createdAt", assistant.getCreatedAt());
        map.put("updatedAt", assistant.getUpdatedAt());
        return map;
    }
}

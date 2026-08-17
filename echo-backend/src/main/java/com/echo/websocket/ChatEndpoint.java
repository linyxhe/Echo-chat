package com.echo.websocket;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.echo.ai.AiChatService;
import com.echo.ai.BotUserService;
import com.echo.config.GetHttpSessionConfig;
import com.echo.file.FileService;
import com.echo.mapper.MessageMapper;
import com.echo.pojo.Message;
import com.echo.service.ConversationService;
import com.echo.service.ContentModerationService;
import com.echo.service.GroupService;
import com.echo.service.AiAssistantService;
import com.echo.service.PresenceService;
import com.echo.utils.JwtUtil;
import com.echo.websocket.pojo.ResultMessage;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.EOFException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/ws", configurator = GetHttpSessionConfig.class)
@Component
public class ChatEndpoint {

    // 在线的用户，key为用户ID，value为该用户的全部存活会话（多标签页：一个用户可有多个 Session）。
    private static final Map<Long, Set<Session>> onlineUsers = new ConcurrentHashMap<>();

    // 保存 Session 到用户ID的映射（onError 定位 userId 用）
    private static final Map<Session, Long> sessionUserIdMap = new ConcurrentHashMap<>();

    // 每个 session 一把写锁：JSR-356 的 BasicRemote 不保证并发写安全，
    // WS 处理线程与 AI 流式回调线程可能同时往同一 session 写（TEXT_FULL_WRITING），须串行化。
    private static final Map<Session, Object> sessionWriteLocks = new ConcurrentHashMap<>();

    // 每个 session 最近一次心跳时间：用于定时清扫淘汰断线但 onClose 未触发的死会话。
    private static final Map<Session, Long> lastHeartbeatAt = new ConcurrentHashMap<>();

    private static final Set<String> CALL_SIGNAL_KINDS = Set.of(
            "OFFER", "ANSWER", "ICE", "RINGING", "DECLINE", "BUSY", "END"
    );
    private static final long CALL_ID_MAX_AGE_MILLIS = 5 * 60 * 1000L;

    private static MessageMapper messageMapper;
    private static JwtUtil jwtUtil;
    private static com.echo.mapper.UserMapper userMapper;
    private static com.echo.mapper.FriendshipMapper friendshipMapper;
    private static FileService fileService;
    private static ConversationService conversationService;
    private static AiChatService aiChatService;
    private static BotUserService botUserService;
    private static PresenceService presenceService;
    private static WsEventPublisher wsEventPublisher;
    private static GroupService groupService;
    private static ContentModerationService contentModerationService;
    private static AiAssistantService aiAssistantService;

    // 心跳超时淘汰阈值（客户端名义 25s 心跳；后台标签页可能被节流到 ~60s，故取 120s）。
    private static long evictIdleSeconds = 120;

    @Value("${app.presence.evict-idle-seconds:120}")
    public void setEvictIdleSeconds(long v) {
        ChatEndpoint.evictIdleSeconds = v;
    }

    @Autowired
    public void setMessageMapper(MessageMapper messageMapper) {
        ChatEndpoint.messageMapper = messageMapper;
    }
    
    @Autowired
    public void setJwtUtil(JwtUtil jwtUtil) {
        ChatEndpoint.jwtUtil = jwtUtil;
    }
    
    @Autowired
    public void setUserMapper(com.echo.mapper.UserMapper userMapper) {
        ChatEndpoint.userMapper = userMapper;
    }

    @Autowired
    public void setFriendshipMapper(com.echo.mapper.FriendshipMapper friendshipMapper) {
        ChatEndpoint.friendshipMapper = friendshipMapper;
    }

    @Autowired
    public void setFileService(FileService fileService) {
        ChatEndpoint.fileService = fileService;
    }

    @Autowired
    public void setConversationService(ConversationService conversationService) {
        ChatEndpoint.conversationService = conversationService;
    }

    @Autowired
    public void setAiChatService(AiChatService aiChatService) {
        ChatEndpoint.aiChatService = aiChatService;
    }

    @Autowired
    public void setBotUserService(BotUserService botUserService) {
        ChatEndpoint.botUserService = botUserService;
    }

    @Autowired
    public void setPresenceService(PresenceService presenceService) {
        ChatEndpoint.presenceService = presenceService;
    }

    @Autowired
    public void setWsEventPublisher(WsEventPublisher wsEventPublisher) {
        ChatEndpoint.wsEventPublisher = wsEventPublisher;
    }

    @Autowired
    public void setGroupService(GroupService groupService) {
        ChatEndpoint.groupService = groupService;
    }

    @Autowired
    public void setContentModerationService(ContentModerationService contentModerationService) {
        ChatEndpoint.contentModerationService = contentModerationService;
    }

    @Autowired
    public void setAiAssistantService(AiAssistantService aiAssistantService) {
        ChatEndpoint.aiAssistantService = aiAssistantService;
    }

    /** 向某用户推送一条 WS 消息（AI 流式用）。经 Redis pub/sub 跨实例投递；发布失败降级为本实例直发。 */
    public static void sendToUser(Long userId, ResultMessage msg) {
        String json = JSON.toJSONString(msg);
        if (wsEventPublisher != null) {
            wsEventPublisher.publishToUser(userId, json);
        } else {
            deliverToUser(userId, json);
        }
    }

    /** 本实例投递：向该用户的所有存活会话发送 JSON（多标签页：每个会话都收到）。有会话送达返回 true。 */
    public static boolean deliverToUser(Long userId, String json) {
        if (userId == null) return false;
        Set<Session> sessions = onlineUsers.get(userId);
        if (sessions == null) return false;
        boolean delivered = false;
        for (Session s : sessions) {
            if (s.isOpen()) {
                sendText(s, json);
                delivered = true;
            }
        }
        return delivered;
    }

    /** 本实例广播给所有在线会话（订阅者投递用）。 */
    public static void deliverBroadcast(String json) {
        for (Set<Session> sessions : onlineUsers.values()) {
            for (Session session : sessions) {
                sendText(session, json);
            }
        }
    }

    /** 串行化对同一 session 的文本发送（同一 session 的所有写都必须走这里）。 */
    private static void sendText(Session session, String text) {
        if (session == null || !session.isOpen()) return;
        Object lock = sessionWriteLocks.computeIfAbsent(session, k -> new Object());
        synchronized (lock) {
            try {
                session.getBasicRemote().sendText(text);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Long userId;

    /**
     * 建立websocket连接后，被调用
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        // 尝试从URL参数获取Token
        String queryString = session.getRequestURI().getQuery();
        String token = null;
        
        if (StringUtils.hasText(queryString)) {
            String[] params = queryString.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
        }
        
        if (StringUtils.hasText(token)) {
            // 验证Token
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    // 检查用户状态
                    com.echo.pojo.User user = userMapper.selectById(userId);
                    if (user != null && user.getStatus() == 1) { // 1: 正常
                        this.userId = userId;
                        onlineUsers.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
                        sessionUserIdMap.put(session, userId);
                        lastHeartbeatAt.put(session, System.currentTimeMillis());
                        if (presenceService != null) presenceService.markOnline(userId);

                        // 广播用户上线通知
                        broadcastUserOnline(userId);
                        return;
                    }
                }
            }
        }
        
        // 如果验证失败或用户状态异常，关闭连接
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Authentication failed or account disabled"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 广播用户上线通知
     */
    private void broadcastUserOnline(Long userId) {
        if (!userWantsOnlineVisible(userId)) return; // 隐私：用户关闭「展示在线状态」则不广播
        ResultMessage message = new ResultMessage();
        message.setType("USER_ONLINE");
        message.setData(Map.of("userId", userId));
        publishBroadcast(message);
    }

    /**
     * 广播用户下线通知
     */
    private void broadcastUserOffline(Long userId) {
        if (!userWantsOnlineVisible(userId)) return;
        ResultMessage message = new ResultMessage();
        message.setType("USER_OFFLINE");
        message.setData(Map.of("userId", userId));
        publishBroadcast(message);
    }

    /** 发布全局广播（经 Redis pub/sub，跨实例）；发布失败降级为本实例广播。 */
    private static void publishBroadcast(ResultMessage message) {
        String json = JSON.toJSONString(message);
        if (wsEventPublisher != null) {
            wsEventPublisher.publishBroadcast(json);
        } else {
            deliverBroadcast(json);
        }
    }

    /** 隐私：用户关闭「展示在线状态」时对他人隐藏（不广播其上下线）。 */
    private static boolean userWantsOnlineVisible(Long userId) {
        if (userMapper == null || userId == null) return true;
        try {
            com.echo.pojo.User u = userMapper.selectById(userId);
            return u == null || Boolean.TRUE.equals(u.getShowOnlineStatus());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 处理客户端发送的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // 解析客户端发送的消息
            Map<String, Object> msgData = JSON.parseObject(message, new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {});
            String type = String.valueOf(msgData.get("type"));
            
            switch (type) {
                case "CHAT_MESSAGE":
                    handleChatMessage(msgData, session);
                    break;
                case "AI_STREAM_CANCEL":
                    handleAiStreamCancel(msgData);
                    break;
                case "MESSAGE_READ":
                    handleMessageRead(msgData, session);
                    break;
                case "CALL_SIGNAL":
                    handleCallSignal(msgData, session);
                    break;
                case "HEARTBEAT":
                    handleHeartbeat(session);
                    break;
                case "TYPING":
                    handleTyping(msgData, session);
                    break;
                case "GROUP_MESSAGE":
                    handleGroupMessage(msgData, session);
                    break;
                default:
                    System.out.println("未知消息类型: " + type);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /** 只允许当前连接所属用户取消自己的 AI 流。 */
    private void handleAiStreamCancel(Map<String, Object> msgData) {
        Object dataObj = msgData.get("data");
        if (!(dataObj instanceof Map<?, ?>)) return;
        Object streamIdObj = ((Map<?, ?>) dataObj).get("streamId");
        if (streamIdObj == null || !StringUtils.hasText(String.valueOf(streamIdObj))) return;
        if (aiChatService != null && this.userId != null) {
            aiChatService.cancel(this.userId, String.valueOf(streamIdObj));
        }
    }

    private void handleCallSignal(Map<String, Object> msgData, Session session) {
        Object dataObj = msgData.get("data");
        if (!(dataObj instanceof Map<?, ?>)) return;
        Map<?, ?> rawData = (Map<?, ?>) dataObj;

        Object toUserIdObj = rawData.get("toUserId");
        Object kindObj = rawData.get("kind");
        String kind = kindObj == null ? "" : String.valueOf(kindObj).trim().toUpperCase();
        String signalCallId = rawData.get("callId") == null ? "" : String.valueOf(rawData.get("callId")).trim();
        boolean requiresAck = "OFFER".equals(kind) || "ANSWER".equals(kind);
        if (toUserIdObj == null || kind.isEmpty()) {
            if (requiresAck) sendCallSignalAck(session, signalCallId, kind, "INVALID");
            return;
        }

        Long toUserId;
        try {
            toUserId = Long.valueOf(String.valueOf(toUserIdObj));
        } catch (Exception e) {
            if (requiresAck) sendCallSignalAck(session, signalCallId, kind, "INVALID");
            return;
        }

        Object callIdObj = rawData.get("callId");
        Object callTypeObj = rawData.get("callType");
        Object payloadObj = rawData.get("payload");

        boolean invalid = this.userId == null
                || toUserId <= 0
                || toUserId.equals(this.userId)
                || !CALL_SIGNAL_KINDS.contains(kind)
                || signalCallId.isEmpty()
                || signalCallId.length() > 128
                || isExpiredCallId(signalCallId)
                || (requiresAck && !isValidSdpPayload(kind, payloadObj));
        if (invalid) {
            if (requiresAck) sendCallSignalAck(session, signalCallId, kind, "INVALID");
            return;
        }

        System.out.println("CALL_SIGNAL from=" + this.userId + " to=" + toUserId + " kind=" + kind);

        ResultMessage forward = new ResultMessage();
        forward.setType("CALL_SIGNAL");

        Map<String, Object> forwardData = new HashMap<>();
        forwardData.put("fromUserId", this.userId);
        forwardData.put("toUserId", toUserId);
        forwardData.put("kind", kind);
        if (callIdObj != null) forwardData.put("callId", callIdObj);
        if (callTypeObj != null) forwardData.put("callType", callTypeObj);
        forwardData.put("payload", payloadObj);
        forward.setData(forwardData);

        // 跨实例判定：用 Redis 共享 presence 判断目标是否在线（本实例无法知道目标会话是否在别的实例）
        boolean targetOnline = presenceService != null
                ? presenceService.isOnline(toUserId)
                : onlineUsers.containsKey(toUserId);
        if (targetOnline) {
            boolean delivered;
            if (wsEventPublisher != null) {
                delivered = wsEventPublisher.publishToUser(toUserId, JSON.toJSONString(forward));
                if (!delivered) delivered = deliverToUser(toUserId, JSON.toJSONString(forward));
            } else {
                delivered = deliverToUser(toUserId, JSON.toJSONString(forward));
            }
            if (requiresAck) sendCallSignalAck(session, signalCallId, kind, delivered ? "DELIVERED" : "OFFLINE");
            if (!delivered) sendCallOffline(session, toUserId, callIdObj, callTypeObj);
        } else {
            if (requiresAck) sendCallSignalAck(session, signalCallId, kind, "OFFLINE");
            sendCallOffline(session, toUserId, callIdObj, callTypeObj);
        }
    }

    private void sendCallOffline(Session session, Long toUserId, Object callIdObj, Object callTypeObj) {
        ResultMessage offline = new ResultMessage();
        offline.setType("CALL_SIGNAL");
        Map<String, Object> offlineData = new HashMap<>();
        offlineData.put("fromUserId", toUserId);
        offlineData.put("toUserId", this.userId);
        offlineData.put("kind", "OFFLINE");
        if (callIdObj != null) offlineData.put("callId", callIdObj);
        if (callTypeObj != null) offlineData.put("callType", callTypeObj);
        offline.setData(offlineData);
        sendText(session, JSON.toJSONString(offline));
    }

    private static boolean isValidSdpPayload(String kind, Object payloadObj) {
        if (!(payloadObj instanceof Map<?, ?> payload)) return false;
        Object sdpObj = payload.get("sdp");
        if (!(sdpObj instanceof Map<?, ?> sdp)) return false;
        String type = sdp.get("type") == null ? "" : String.valueOf(sdp.get("type"));
        String value = sdp.get("sdp") == null ? "" : String.valueOf(sdp.get("sdp"));
        return kind.toLowerCase().equals(type.toLowerCase()) && StringUtils.hasText(value);
    }

    private static boolean isExpiredCallId(String value) {
        int separator = value.indexOf('-');
        if (separator < 10) return false; // Backward-compatible UUID from older clients.
        try {
            long createdAt = Long.parseLong(value.substring(0, separator));
            long age = System.currentTimeMillis() - createdAt;
            return age > CALL_ID_MAX_AGE_MILLIS || age < -60_000L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void sendCallSignalAck(Session session, String callId, String kind, String status) {
        ResultMessage ack = new ResultMessage();
        ack.setType("CALL_SIGNAL_ACK");
        Map<String, Object> data = new HashMap<>();
        data.put("callId", callId == null ? "" : callId);
        data.put("kind", kind == null ? "" : kind);
        data.put("status", status);
        ack.setData(data);
        sendText(session, JSON.toJSONString(ack));
    }
    
    /**
     * 处理聊天消息
     */
    private void handleChatMessage(Map<String, Object> msgData, Session session) throws IOException {
        Object dataObj = msgData.get("data");
        if (dataObj instanceof Map<?, ?>) {
            Map<?, ?> rawData = (Map<?, ?>) dataObj;
            
            Object receiverIdObj = rawData.get("receiverId");
            Object messageTypeObj = rawData.get("messageType");
            Object contentObj = rawData.get("content");
            Object clientMessageIdObj = rawData.get("clientMessageId");
            
            if (receiverIdObj != null && messageTypeObj != null && contentObj != null) {
                Long receiverId = Long.valueOf(receiverIdObj.toString());
                String messageType = String.valueOf(messageTypeObj);
                String content = String.valueOf(contentObj);
                String clientMessageId = clientMessageIdObj != null ? String.valueOf(clientMessageIdObj) : null;

                // 好友关系校验：系统助手公开；用户自定义助手仅创建者可访问。
                boolean isBotTarget = botUserService != null && botUserService.isBotUserId(receiverId);
                boolean canChatWithBot = isBotTarget && (aiAssistantService == null
                        || aiAssistantService.canChat(this.userId, receiverId));
                boolean isFriend = false;
                if (friendshipMapper != null && !isBotTarget) {
                    Long count = friendshipMapper.selectCount(
                            new QueryWrapper<com.echo.pojo.Friendship>()
                                    .eq("user_id", this.userId)
                                    .eq("friend_id", receiverId)
                                    .eq("status", 1)
                    );
                    isFriend = count != null && count > 0;
                }
                if (isBotTarget && !canChatWithBot) {
                    sendContentRejectedAck(session, clientMessageId, "无权访问该 AI 助手");
                    return;
                }
                if (!isFriend && !isBotTarget) {
                    ResultMessage ackMessage = new ResultMessage();
                    ackMessage.setType("MESSAGE_ACK");
                    Map<String, Object> ackData = new HashMap<>();
                    ackData.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
                    ackData.put("status", "REJECTED_NOT_FRIEND");
                    ackData.put("reason", "未添加该好友，请先添加后再发送");
                    ackMessage.setData(ackData);
                    sendText(session, JSON.toJSONString(ackMessage));
                    return;
                }

                if ("TEXT".equalsIgnoreCase(messageType) && contentModerationService != null
                        && contentModerationService.findMatchedWord(content) != null) {
                    sendContentRejectedAck(session, clientMessageId, "消息包含敏感词，无法发送");
                    return;
                }

                // 文件类消息的 URL 必须指向受控 /files/**（旧 /upload/** 公开目录已关闭）。
                String controlledFileUrl = null;
                if ("IMAGE".equalsIgnoreCase(messageType)) {
                    controlledFileUrl = content;
                } else if ("FILE".equalsIgnoreCase(messageType) || "AUDIO".equalsIgnoreCase(messageType)) {
                    try {
                        Map<String, Object> fileInfo = JSON.parseObject(content, new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {});
                        Object urlObj = fileInfo.get("url");
                        if (urlObj != null) controlledFileUrl = String.valueOf(urlObj);
                    } catch (Exception ignored) { }
                }
                if (controlledFileUrl != null && controlledFileUrl.startsWith("/files/")) {
                    if (fileService == null || fileService.findReadyChatFile(controlledFileUrl, this.userId, receiverId) == null) {
                        ResultMessage ackMessage = new ResultMessage();
                        ackMessage.setType("MESSAGE_ACK");
                        Map<String, Object> ackData = new HashMap<>();
                        ackData.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
                        ackData.put("status", "REJECTED_FILE_NOT_READY");
                        ackData.put("reason", "文件尚未完成校验，暂不能发送");
                        ackMessage.setData(ackData);
                        sendText(session, JSON.toJSONString(ackMessage));
                        return;
                    }
                }

                // Client message IDs make a recovered large-file send idempotent.
                // A refresh between WebSocket send and ACK must not create a duplicate message.
                if (StringUtils.hasText(clientMessageId)) {
                    Message existingMessage = messageMapper.selectOne(new QueryWrapper<Message>()
                            .eq("sender_id", this.userId)
                            .eq("client_message_id", clientMessageId));
                    if (existingMessage != null) {
                        sendMessageAck(session, clientMessageId, existingMessage.getId(), "SENT", null);
                        return;
                    }
                }

                LocalDateTime now = LocalDateTime.now();
                Message message = new Message();
                message.setSenderId(this.userId);
                message.setReceiverId(receiverId);
                message.setClientMessageId(StringUtils.hasText(clientMessageId) ? clientMessageId : null);
                message.setMessageType(messageType);
                message.setIsRead(false);
                message.setDeletedBySender(false);
                message.setDeletedByReceiver(false);
                message.setCreatedAt(now);
                
                if ("FILE".equalsIgnoreCase(messageType) || "AUDIO".equalsIgnoreCase(messageType)) {
                    try {
                        Map<String, Object> fileInfo = JSON.parseObject(content, new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {});
                        Object urlObj = fileInfo.get("url");
                        Object nameObj = fileInfo.get("name");
                        Object sizeObj = fileInfo.get("size");
                        if (urlObj != null) message.setFileUrl(String.valueOf(urlObj));
                        if (nameObj != null) message.setFileName(String.valueOf(nameObj));
                        if (sizeObj != null) {
                            try {
                                message.setFileSize(Long.valueOf(String.valueOf(sizeObj)));
                            } catch (Exception ignored) {
                            }
                        }
                        message.setContent(message.getFileName() != null ? message.getFileName() : content);
                    } catch (Exception e) {
                        message.setContent(content);
                    }
                } else {
                    message.setContent(content);
                }

                messageMapper.insert(message);

                if (conversationService != null) {
                    conversationService.updateOnSend(this.userId, receiverId, message.getId(), now);
                }

                sendMessageAck(session, clientMessageId, message.getId(), "SENT", now.toString());

                // 发给 AI 助手：ACK 之后异步触发流式回复（不阻塞 WS 线程；回调在 langchain4j 线程）
                if (isBotTarget && aiChatService != null) {
                    try {
                        aiChatService.handleUserMessage(this.userId, message, clientMessageId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 3. 转发消息给接收者（经 Redis pub/sub 跨实例投递到其全部会话；离线则订阅者无会话可投）
                ResultMessage chatMessage = new ResultMessage();
                chatMessage.setType("NEW_MESSAGE");
                Map<String, Object> chatData = new HashMap<>();
                chatData.put("id", message.getId());
                chatData.put("senderId", this.userId);
                chatData.put("receiverId", receiverId);
                chatData.put("content", message.getContent());
                chatData.put("messageType", messageType);
                chatData.put("createdAt", now.toString());
                chatData.put("isRead", false);
                if (message.getFileUrl() != null) chatData.put("fileUrl", message.getFileUrl());
                if (message.getFileName() != null) chatData.put("fileName", message.getFileName());
                if (message.getFileSize() != null) chatData.put("fileSize", message.getFileSize());
                chatMessage.setData(chatData);

                chatMessage.setTimestamp(System.currentTimeMillis());
                chatMessage.setMessageId(UUID.randomUUID().toString());

                if (wsEventPublisher != null) {
                    wsEventPublisher.publishToUser(receiverId, JSON.toJSONString(chatMessage));
                } else {
                    deliverToUser(receiverId, JSON.toJSONString(chatMessage));
                }
            }
        }
    }

    /** 从 FILE 消息 content JSON 提取文件信息（url/name/size）。 */
    private Map<String, Object> extractFileInfo(String content) {
        try {
            Map<String, Object> fileInfo = JSON.parseObject(content, new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {});
            return fileInfo != null ? fileInfo : new HashMap<>();
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private void sendMessageAck(Session session, String clientMessageId, Long serverMessageId,
                                String status, String createdAt) throws IOException {
        ResultMessage ackMessage = new ResultMessage();
        ackMessage.setType("MESSAGE_ACK");
        Map<String, Object> ackData = new HashMap<>();
        ackData.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
        ackData.put("serverMessageId", serverMessageId);
        ackData.put("status", status);
        if (createdAt != null) ackData.put("createdAt", createdAt);
        ackMessage.setData(ackData);
        sendText(session, JSON.toJSONString(ackMessage));
    }

    private void sendContentRejectedAck(Session session, String clientMessageId, String reason) throws IOException {
        ResultMessage ackMessage = new ResultMessage();
        ackMessage.setType("MESSAGE_ACK");
        Map<String, Object> data = new HashMap<>();
        data.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
        data.put("status", "REJECTED_CONTENT");
        data.put("reason", reason);
        ackMessage.setData(data);
        sendText(session, JSON.toJSONString(ackMessage));
    }

    /**
     * 处理消息已读确认
     */
    private void handleMessageRead(Map<String, Object> msgData, Session session) {
        Object dataObj = msgData.get("data");
        if (dataObj == null) return;
        
        Map<String, Object> data = (Map<String, Object>) dataObj;
        
        Object senderIdObj = data.get("senderId");
        if (senderIdObj == null) return;
        
        Object messageIdsObj = data.get("messageIds");
        if (messageIdsObj == null) return;
        
        try {
            Long senderId = Long.valueOf(senderIdObj.toString());
            LocalDateTime now = LocalDateTime.now();

            // 隐私：读者关闭「展示已读回执」时不发回执、不写 is_read/read_at（发送者见「已发送」）
            boolean shareReceipt = userWantsReadReceipts(this.userId);
            if (shareReceipt) {
                ResultMessage readReceipt = new ResultMessage();
                readReceipt.setType("MESSAGE_READ_RECEIPT");
                readReceipt.setData(Map.of(
                    "messageIds", messageIdsObj,
                    "readerId", this.userId,
                    "readAt", now.toString()
                ));

                if (wsEventPublisher != null) {
                    wsEventPublisher.publishToUser(senderId, JSON.toJSONString(readReceipt));
                } else {
                    deliverToUser(senderId, JSON.toJSONString(readReceipt));
                }

                // 更新数据库中消息的已读状态与已读时间
                UpdateWrapper<Message> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("sender_id", senderId)
                             .eq("receiver_id", this.userId)
                             .eq("is_read", false)
                             .set("is_read", true)
                             .set("read_at", now);
                messageMapper.update(null, updateWrapper);
            }

            // 无论是否共享回执，都清读者未读徽标
            if (conversationService != null) {
                conversationService.markRead(this.userId, senderId);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 隐私：用户（读者）是否展示已读回执。 */
    private static boolean userWantsReadReceipts(Long userId) {
        if (userMapper == null || userId == null) return true;
        try {
            com.echo.pojo.User u = userMapper.selectById(userId);
            return u == null || Boolean.TRUE.equals(u.getShowReadReceipts());
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 处理心跳包
     */
    private void handleHeartbeat(Session session) throws IOException {
        lastHeartbeatAt.put(session, System.currentTimeMillis());
        if (presenceService != null && this.userId != null) {
            presenceService.refresh(this.userId);
        }
        ResultMessage heartbeatResponse = new ResultMessage();
        heartbeatResponse.setType("HEARTBEAT");
        sendText(session, JSON.toJSONString(heartbeatResponse));
    }
    
    /**
     * 处理正在输入状态
     */
    private void handleTyping(Map<String, Object> msgData, Session session) {
        Object dataObj = msgData.get("data");
        if (dataObj instanceof Map<?, ?>) {
            Map<?, ?> rawData = (Map<?, ?>) dataObj;
            Object friendIdObj = rawData.get("friendId");
            if (friendIdObj != null) {
                Long friendId = Long.valueOf(friendIdObj.toString());
                
                if (onlineUsers.containsKey(friendId)) {
                    ResultMessage typingMessage = new ResultMessage();
                    typingMessage.setType("TYPING");
                    typingMessage.setData(Map.of(
                        "userId", this.userId
                    ));

                    if (wsEventPublisher != null) {
                        wsEventPublisher.publishToUser(friendId, JSON.toJSONString(typingMessage));
                    } else {
                        deliverToUser(friendId, JSON.toJSONString(typingMessage));
                    }
                }
            }
        }
    }

    /**
     * 处理群聊消息：校验成员 → 幂等落库 → ACK → 向群全部成员广播（跨实例 pub/sub）。
     */
    private void handleGroupMessage(Map<String, Object> msgData, Session session) throws IOException {
        Object dataObj = msgData.get("data");
        if (!(dataObj instanceof Map<?, ?>)) return;
        Map<?, ?> rawData = (Map<?, ?>) dataObj;

        Object groupIdObj = rawData.get("groupId");
        Object contentObj = rawData.get("content");
        Object messageTypeObj = rawData.get("messageType");
        Object clientMessageIdObj = rawData.get("clientMessageId");
        if (groupIdObj == null || contentObj == null) return;

        Long groupId;
        try {
            groupId = Long.valueOf(String.valueOf(groupIdObj));
        } catch (Exception e) {
            return;
        }
        String content = String.valueOf(contentObj);
        String messageType = messageTypeObj != null ? String.valueOf(messageTypeObj) : "TEXT";
        String clientMessageId = clientMessageIdObj != null ? String.valueOf(clientMessageIdObj) : null;

        if (groupService == null) return;
        if (!groupService.isMember(groupId, this.userId)) {
            sendMessageAck(session, clientMessageId, null, "REJECTED_NOT_MEMBER", null);
            return;
        }

        if ("TEXT".equalsIgnoreCase(messageType) && contentModerationService != null
                && contentModerationService.findMatchedWord(content) != null) {
            sendContentRejectedAck(session, clientMessageId, "群消息包含敏感词，无法发送");
            return;
        }

        // 文件/图片消息：URL 必须指向受控 /files/** 且就绪（群文件校验），否则拒绝发送。
        String controlledFileUrl = null;
        String fileUrl = null, fileName = null;
        Long fileSize = null;
        if ("IMAGE".equalsIgnoreCase(messageType)) {
            controlledFileUrl = content;
        } else if ("FILE".equalsIgnoreCase(messageType) || "AUDIO".equalsIgnoreCase(messageType)) {
            Map<String, Object> fileInfo = extractFileInfo(content);
            Object urlObj = fileInfo.get("url");
            if (urlObj != null) {
                controlledFileUrl = String.valueOf(urlObj);
                fileUrl = String.valueOf(urlObj);
            }
            Object nameObj = fileInfo.get("name");
            if (nameObj != null) fileName = String.valueOf(nameObj);
            Object sizeObj = fileInfo.get("size");
            if (sizeObj != null) {
                try { fileSize = Long.valueOf(String.valueOf(sizeObj)); } catch (Exception ignored) { }
            }
        }
        if (controlledFileUrl != null && controlledFileUrl.startsWith("/files/")) {
            if (fileService == null || fileService.findReadyGroupFile(controlledFileUrl, this.userId, groupId) == null) {
                ResultMessage reject = new ResultMessage();
                reject.setType("MESSAGE_ACK");
                Map<String, Object> rejectData = new HashMap<>();
                rejectData.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
                rejectData.put("status", "REJECTED_FILE_NOT_READY");
                rejectData.put("reason", "文件尚未完成校验，暂不能发送");
                reject.setData(rejectData);
                sendText(session, JSON.toJSONString(reject));
                return;
            }
        }

        // IMAGE 的 content 即图片 URL；FILE 的 content 落文件名（与 1:1 一致）。
        String persistedContent = content;
        if (("FILE".equalsIgnoreCase(messageType) || "AUDIO".equalsIgnoreCase(messageType)) && fileName != null) {
            persistedContent = fileName;
        }

        com.echo.pojo.ChatGroupMessage msg = groupService.persistMessage(
                groupId, this.userId, clientMessageId, persistedContent, messageType,
                fileUrl, fileName, fileSize);
        sendMessageAck(session, clientMessageId, msg.getId(), "SENT",
                msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);

        ResultMessage gm = new ResultMessage();
        gm.setType("GROUP_MESSAGE");
        Map<String, Object> d = new HashMap<>();
        d.put("id", msg.getId());
        d.put("groupId", groupId);
        d.put("senderId", this.userId);
        d.put("clientMessageId", msg.getClientMessageId()); // 前端据此去重乐观气泡
        d.put("content", msg.getContent());
        d.put("messageType", messageType);
        d.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
        if (msg.getFileUrl() != null) d.put("fileUrl", msg.getFileUrl());
        if (msg.getFileName() != null) d.put("fileName", msg.getFileName());
        if (msg.getFileSize() != null) d.put("fileSize", msg.getFileSize());
        gm.setData(d);
        gm.setTimestamp(System.currentTimeMillis());
        gm.setMessageId(UUID.randomUUID().toString());
        groupService.broadcastToMembers(groupId, JSON.toJSONString(gm));
    }

    /**
     * 断开 websocket 连接时被调用
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessionWriteLocks.remove(session);
        lastHeartbeatAt.remove(session);
        if (this.userId != null) {
            sessionUserIdMap.remove(session);
            // 从该用户会话集合移除本会话；仅当集合清空（用户最后一个会话关闭）才标记离线并广播。
            Set<Session> sessions = onlineUsers.get(this.userId);
            boolean lastClosed = true;
            if (sessions != null) {
                sessions.remove(session);
                if (!sessions.isEmpty()) {
                    lastClosed = false;
                } else {
                    onlineUsers.remove(this.userId, sessions);
                }
            }
            if (lastClosed) {
                if (presenceService != null) presenceService.markOffline(this.userId);
                broadcastUserOffline(this.userId);
            }
        }
    }

    /**
     * 处理WebSocket错误
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        sessionWriteLocks.remove(session);
        lastHeartbeatAt.remove(session);
        Long userId = sessionUserIdMap.get(session);
        if (userId != null) {
            sessionUserIdMap.remove(session);
            // 从该用户会话集合移除本会话；仅当集合清空才标记离线并广播。
            Set<Session> sessions = onlineUsers.get(userId);
            boolean lastClosed = true;
            if (sessions != null) {
                sessions.remove(session);
                if (!sessions.isEmpty()) {
                    lastClosed = false;
                } else {
                    onlineUsers.remove(userId, sessions);
                }
            }
            if (lastClosed) {
                if (presenceService != null) presenceService.markOffline(userId);
                broadcastUserOffline(userId);
            }
        }
        // EOFException 是客户端异常断开（关闭浏览器/网络中断）的正常现象，不需要打印堆栈
        if (throwable instanceof EOFException) {
            System.out.println("WebSocket客户端异常断开连接: userId=" + userId);
        } else {
            throwable.printStackTrace();
        }
    }

    /**
     * 定时清扫：关闭心跳超时的死会话（触发 onClose → 完整清理 + 标记离线），
     * 并清理 Redis presence ZSET 中超过 TTL 的陈旧成员。
     */
    @Scheduled(fixedDelayString = "${app.presence.sweep-interval-ms:60000}")
    public void sweepStaleSessions() {
        long now = System.currentTimeMillis();
        long idleLimit = evictIdleSeconds * 1000L;
        for (Map.Entry<Session, Long> entry : lastHeartbeatAt.entrySet()) {
            if (now - entry.getValue() > idleLimit) {
                Session s = entry.getKey();
                if (s != null && s.isOpen()) {
                    try {
                        s.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Heartbeat timeout"));
                    } catch (Exception e) {
                        // 关闭失败由 onClose/onError 兜底清理
                    }
                }
            }
        }
        if (presenceService != null) presenceService.sweepStale();
    }
}

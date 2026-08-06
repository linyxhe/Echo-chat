package com.echo.websocket;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.echo.config.GetHttpSessionConfig;
import com.echo.mapper.ConversationMapper;
import com.echo.mapper.MessageMapper;
import com.echo.pojo.Conversation;
import com.echo.pojo.Message;
import com.echo.utils.JwtUtil;
import com.echo.websocket.pojo.ResultMessage;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.EOFException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/ws", configurator = GetHttpSessionConfig.class)
@Component
public class ChatEndpoint {

    // 保存在线的用户，key为用户ID，value为 Session 对象
    private static final Map<Long, Session> onlineUsers = new ConcurrentHashMap<>();
    
    // 保存用户ID到Session的映射，用于快速查找
    private static final Map<Session, Long> sessionUserIdMap = new ConcurrentHashMap<>();

    private static MessageMapper messageMapper;
    private static JwtUtil jwtUtil;
    private static ConversationMapper conversationMapper;
    private static com.echo.mapper.UserMapper userMapper;
    private static com.echo.mapper.FriendshipMapper friendshipMapper;
    
    @Autowired
    public void setMessageMapper(MessageMapper messageMapper) {
        ChatEndpoint.messageMapper = messageMapper;
    }
    
    @Autowired
    public void setJwtUtil(JwtUtil jwtUtil) {
        ChatEndpoint.jwtUtil = jwtUtil;
    }
    
    @Autowired
    public void setConversationMapper(ConversationMapper conversationMapper) {
        ChatEndpoint.conversationMapper = conversationMapper;
    }

    @Autowired
    public void setUserMapper(com.echo.mapper.UserMapper userMapper) {
        ChatEndpoint.userMapper = userMapper;
    }
    
    @Autowired
    public void setFriendshipMapper(com.echo.mapper.FriendshipMapper friendshipMapper) {
        ChatEndpoint.friendshipMapper = friendshipMapper;
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
                        onlineUsers.put(userId, session);
                        sessionUserIdMap.put(session, userId);
                        
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
        ResultMessage message = new ResultMessage();
        message.setType("USER_ONLINE");
        message.setData(Map.of("userId", userId));
        broadcastToAll(message);
    }
    
    /**
     * 广播用户下线通知
     */
    private void broadcastUserOffline(Long userId) {
        ResultMessage message = new ResultMessage();
        message.setType("USER_OFFLINE");
        message.setData(Map.of("userId", userId));
        broadcastToAll(message);
    }
    
    /**
     * 广播消息给所有在线用户
     */
    private void broadcastToAll(ResultMessage message) {
        String jsonMessage = JSON.toJSONString(message);
        for (Session session : onlineUsers.values()) {
            try {
                session.getBasicRemote().sendText(jsonMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
                default:
                    System.out.println("未知消息类型: " + type);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void handleCallSignal(Map<String, Object> msgData, Session session) {
        Object dataObj = msgData.get("data");
        if (!(dataObj instanceof Map<?, ?>)) return;
        Map<?, ?> rawData = (Map<?, ?>) dataObj;

        Object toUserIdObj = rawData.get("toUserId");
        Object kindObj = rawData.get("kind");
        if (toUserIdObj == null || kindObj == null) return;

        Long toUserId;
        try {
            toUserId = Long.valueOf(String.valueOf(toUserIdObj));
        } catch (Exception e) {
            return;
        }

        String kind = String.valueOf(kindObj);
        Object callIdObj = rawData.get("callId");
        Object callTypeObj = rawData.get("callType");
        Object payloadObj = rawData.get("payload");

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

        if (onlineUsers.containsKey(toUserId)) {
            Session receiverSession = onlineUsers.get(toUserId);
            try {
                receiverSession.getBasicRemote().sendText(JSON.toJSONString(forward));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            ResultMessage offline = new ResultMessage();
            offline.setType("CALL_SIGNAL");
            Map<String, Object> offlineData = new HashMap<>();
            offlineData.put("fromUserId", toUserId);
            offlineData.put("toUserId", this.userId);
            offlineData.put("kind", "OFFLINE");
            if (callIdObj != null) offlineData.put("callId", callIdObj);
            if (callTypeObj != null) offlineData.put("callType", callTypeObj);
            offline.setData(offlineData);
            try {
                session.getBasicRemote().sendText(JSON.toJSONString(offline));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

                // 好友关系校验
                boolean isFriend = false;
                if (friendshipMapper != null) {
                    Long count = friendshipMapper.selectCount(
                            new QueryWrapper<com.echo.pojo.Friendship>()
                                    .eq("user_id", this.userId)
                                    .eq("friend_id", receiverId)
                                    .eq("status", 1)
                    );
                    isFriend = count != null && count > 0;
                }
                if (!isFriend) {
                    ResultMessage ackMessage = new ResultMessage();
                    ackMessage.setType("MESSAGE_ACK");
                    Map<String, Object> ackData = new HashMap<>();
                    ackData.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
                    ackData.put("status", "REJECTED_NOT_FRIEND");
                    ackData.put("reason", "未添加该好友，请先添加后再发送");
                    ackMessage.setData(ackData);
                    session.getBasicRemote().sendText(JSON.toJSONString(ackMessage));
                    return;
                }

                LocalDateTime now = LocalDateTime.now();
                Message message = new Message();
                message.setSenderId(this.userId);
                message.setReceiverId(receiverId);
                message.setMessageType(messageType);
                message.setIsRead(false);
                message.setDeletedBySender(false);
                message.setDeletedByReceiver(false);
                message.setCreatedAt(now);
                
                if ("FILE".equalsIgnoreCase(messageType)) {
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

                updateConversationOnSend(this.userId, receiverId, message.getId(), now);

                ResultMessage ackMessage = new ResultMessage();
                ackMessage.setType("MESSAGE_ACK");
                Map<String, Object> ackData = new HashMap<>();
                ackData.put("clientMessageId", clientMessageId != null ? clientMessageId : "");
                ackData.put("serverMessageId", message.getId());
                ackData.put("status", "SENT");
                ackData.put("createdAt", now.toString());
                ackMessage.setData(ackData);
                session.getBasicRemote().sendText(JSON.toJSONString(ackMessage));
                
                // 3. 转发消息给接收者
                if (onlineUsers.containsKey(receiverId)) {
                    Session receiverSession = onlineUsers.get(receiverId);
                    
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
                    
                    receiverSession.getBasicRemote().sendText(JSON.toJSONString(chatMessage));
                }
            }
        }
    }

    private void updateConversationOnSend(Long senderId, Long receiverId, Long messageId, LocalDateTime now) {
        if (conversationMapper == null) return;

        Conversation senderConv = conversationMapper.selectOne(
                new QueryWrapper<Conversation>().eq("user1_id", senderId).eq("user2_id", receiverId)
        );
        if (senderConv == null) {
            senderConv = new Conversation();
            senderConv.setUser1Id(senderId);
            senderConv.setUser2Id(receiverId);
            senderConv.setLastMessageId(messageId);
            senderConv.setUnreadCount(0);
            senderConv.setUpdatedAt(now);
            conversationMapper.insert(senderConv);
        } else {
            senderConv.setLastMessageId(messageId);
            senderConv.setUpdatedAt(now);
            if (senderConv.getUnreadCount() == null) senderConv.setUnreadCount(0);
            conversationMapper.updateById(senderConv);
        }

        Conversation receiverConv = conversationMapper.selectOne(
                new QueryWrapper<Conversation>().eq("user1_id", receiverId).eq("user2_id", senderId)
        );
        if (receiverConv == null) {
            receiverConv = new Conversation();
            receiverConv.setUser1Id(receiverId);
            receiverConv.setUser2Id(senderId);
            receiverConv.setLastMessageId(messageId);
            receiverConv.setUnreadCount(1);
            receiverConv.setUpdatedAt(now);
            conversationMapper.insert(receiverConv);
        } else {
            receiverConv.setLastMessageId(messageId);
            receiverConv.setUpdatedAt(now);
            int current = receiverConv.getUnreadCount() != null ? receiverConv.getUnreadCount() : 0;
            receiverConv.setUnreadCount(current + 1);
            conversationMapper.updateById(receiverConv);
        }
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
            
            // 发送已读回执给发送者
            if (onlineUsers.containsKey(senderId)) {
                ResultMessage readReceipt = new ResultMessage();
                readReceipt.setType("MESSAGE_READ_RECEIPT");
                readReceipt.setData(Map.of(
                    "messageIds", messageIdsObj,
                    "readerId", this.userId
                ));
                
                try {
                    onlineUsers.get(senderId).getBasicRemote().sendText(JSON.toJSONString(readReceipt));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            // 更新数据库中消息的已读状态
            UpdateWrapper<Message> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("sender_id", senderId)
                         .eq("receiver_id", this.userId)
                         .eq("is_read", false)
                         .set("is_read", true);
            messageMapper.update(null, updateWrapper);

            if (conversationMapper != null) {
                Conversation conv = conversationMapper.selectOne(
                        new QueryWrapper<Conversation>().eq("user1_id", this.userId).eq("user2_id", senderId)
                );
                if (conv != null) {
                    conv.setUnreadCount(0);
                    conv.setUpdatedAt(LocalDateTime.now());
                    conversationMapper.updateById(conv);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理心跳包
     */
    private void handleHeartbeat(Session session) throws IOException {
        ResultMessage heartbeatResponse = new ResultMessage();
        heartbeatResponse.setType("HEARTBEAT");
        session.getBasicRemote().sendText(JSON.toJSONString(heartbeatResponse));
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
                    
                    try {
                        onlineUsers.get(friendId).getBasicRemote().sendText(JSON.toJSONString(typingMessage));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 断开 websocket 连接时被调用
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        if (this.userId != null) {
            sessionUserIdMap.remove(session);
            // 旧连接关闭时不能删除同一用户已经建立的新连接。
            if (onlineUsers.remove(this.userId, session)) {
                broadcastUserOffline(this.userId);
            }
        }
    }
    
    /**
     * 处理WebSocket错误
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        Long userId = sessionUserIdMap.get(session);
        if (userId != null) {
            sessionUserIdMap.remove(session);
            // 只有当前在线映射仍指向出错连接时，才广播下线。
            if (onlineUsers.remove(userId, session)) {
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
}

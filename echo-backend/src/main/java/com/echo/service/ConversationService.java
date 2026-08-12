package com.echo.service;

import java.time.LocalDateTime;

/**
 * 会话（conversation）更新逻辑，供 WebSocket 与 AI 回复共用。
 */
public interface ConversationService {

    /** 发送一条消息后更新收发双方的会话行（双向两行：last_message_id / unread_count）。 */
    void updateOnSend(Long senderId, Long receiverId, Long messageId, LocalDateTime now);

    /** 用户读消息后清零其与某好友会话的 unread_count。 */
    void markRead(Long userId, Long friendId);

    /** 清空会话：重置 userA 方向的会话行（unread_count=0、last_message_id=null），不动 userB 方向。 */
    void clearConversation(Long userA, Long userB);
}

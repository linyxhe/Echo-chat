package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.ConversationMapper;
import com.echo.pojo.Conversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ConversationMapper conversationMapper;

    @Override
    public void updateOnSend(Long senderId, Long receiverId, Long messageId, LocalDateTime now) {
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
            senderConv.setIsArchived(false);
            senderConv.setIsPinned(true);
            senderConv.setUpdatedAt(now);
            conversationMapper.insert(senderConv);
        } else {
            senderConv.setLastMessageId(messageId);
            senderConv.setUpdatedAt(now);
            if (senderConv.getUnreadCount() == null) senderConv.setUnreadCount(0);
            senderConv.setIsArchived(false);
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
            receiverConv.setIsArchived(false);
            receiverConv.setIsPinned(true);
            receiverConv.setUpdatedAt(now);
            conversationMapper.insert(receiverConv);
        } else {
            receiverConv.setLastMessageId(messageId);
            receiverConv.setUpdatedAt(now);
            int current = receiverConv.getUnreadCount() != null ? receiverConv.getUnreadCount() : 0;
            receiverConv.setUnreadCount(current + 1);
            receiverConv.setIsArchived(false);
            conversationMapper.updateById(receiverConv);
        }
    }

    @Override
    public void markRead(Long userId, Long friendId) {
        if (conversationMapper == null) return;
        Conversation conv = conversationMapper.selectOne(
                new QueryWrapper<Conversation>().eq("user1_id", userId).eq("user2_id", friendId)
        );
        if (conv != null) {
            conv.setUnreadCount(0);
            conv.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }

    @Override
    public void clearConversation(Long userA, Long userB) {
        if (conversationMapper == null) return;
        Conversation conv = conversationMapper.selectOne(
                new QueryWrapper<Conversation>().eq("user1_id", userA).eq("user2_id", userB)
        );
        if (conv != null) {
            conv.setUnreadCount(0);
            conv.setLastMessageId(null);
            conv.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }
}

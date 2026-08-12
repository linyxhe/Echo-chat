package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.ChatGroupMapper;
import com.echo.mapper.ChatGroupMemberMapper;
import com.echo.mapper.ChatGroupMessageMapper;
import com.echo.mapper.FriendshipMapper;
import com.echo.mapper.MessageMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.ChatGroup;
import com.echo.pojo.ChatGroupMember;
import com.echo.pojo.ChatGroupMessage;
import com.echo.pojo.Message;
import com.echo.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全文搜索：用户 + 我的 1:1 聊天记录 + 我所在群的群消息。
 */
@Service
public class SearchService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ChatGroupMemberMapper memberMapper;

    @Autowired
    private ChatGroupMapper groupMapper;

    @Autowired
    private ChatGroupMessageMapper groupMessageMapper;

    public List<Map<String, Object>> searchUsers(String keyword, Long currentUserId, int limit) {
        if (!StringUtils.hasText(keyword)) return List.of();
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.and(w -> w.like("username", keyword).or().like("nickname", keyword))
          .ne("id", currentUserId)
          .ne("role", "BOT")
          .last("LIMIT " + Math.min(limit, 20));
        return userMapper.selectList(qw).stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("nickname", u.getNickname());
            map.put("avatarUrl", u.getAvatarUrl());
            boolean isFriend = friendshipMapper.selectCount(new QueryWrapper<com.echo.pojo.Friendship>()
                    .eq("user_id", currentUserId).eq("friend_id", u.getId()).eq("status", 1)) > 0;
            map.put("isFriend", isFriend);
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> searchFriendMessages(String keyword, Long currentUserId, int limit) {
        if (!StringUtils.hasText(keyword)) return List.of();
        QueryWrapper<Message> qw = new QueryWrapper<>();
        qw.and(w -> w
                .nested(a -> a.eq("sender_id", currentUserId).eq("deleted_by_sender", false))
                .or()
                .nested(a -> a.eq("receiver_id", currentUserId).eq("deleted_by_receiver", false)))
          .like("content", keyword)
          .orderByDesc("created_at")
          .last("LIMIT " + Math.min(limit, 20));
        return messageMapper.selectList(qw).stream().map(m -> {
            Long friendId = currentUserId.equals(m.getSenderId()) ? m.getReceiverId() : m.getSenderId();
            User friend = userMapper.selectById(friendId);
            Map<String, Object> map = new HashMap<>();
            map.put("type", "FRIEND_MESSAGE");
            map.put("messageId", m.getId());
            map.put("friendId", friendId);
            map.put("friendNickname", friend != null ? friend.getNickname() : "用户" + friendId);
            map.put("friendAvatar", friend != null ? friend.getAvatarUrl() : null);
            map.put("content", m.getContent());
            map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> searchGroupMessages(String keyword, Long currentUserId, int limit) {
        if (!StringUtils.hasText(keyword)) return List.of();
        List<ChatGroupMember> memberships = memberMapper.selectList(
                new QueryWrapper<ChatGroupMember>().eq("user_id", currentUserId));
        if (memberships.isEmpty()) return List.of();
        Set<Long> groupIds = memberships.stream().map(ChatGroupMember::getGroupId).collect(Collectors.toSet());
        Map<Long, ChatGroup> groupMap = groupMapper.selectList(
                        new QueryWrapper<ChatGroup>().in("id", groupIds)).stream()
                .collect(Collectors.toMap(ChatGroup::getId, g -> g, (a, b) -> a));

        QueryWrapper<ChatGroupMessage> qw = new QueryWrapper<>();
        qw.in("group_id", groupIds).like("content", keyword)
          .orderByDesc("created_at").last("LIMIT " + Math.min(limit, 20));
        return groupMessageMapper.selectList(qw).stream().map(m -> {
            User sender = userMapper.selectById(m.getSenderId());
            ChatGroup g = groupMap.get(m.getGroupId());
            Map<String, Object> map = new HashMap<>();
            map.put("type", "GROUP_MESSAGE");
            map.put("messageId", m.getId());
            map.put("groupId", m.getGroupId());
            map.put("groupName", g != null ? g.getName() : "群");
            map.put("senderNickname", sender != null ? sender.getNickname() : "成员");
            map.put("content", m.getContent());
            map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            return map;
        }).collect(Collectors.toList());
    }
}

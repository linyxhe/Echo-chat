package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.ChatGroupMapper;
import com.echo.mapper.ChatGroupMemberMapper;
import com.echo.mapper.ChatGroupMessageMapper;
import com.echo.mapper.ChatGroupInvitationMapper;
import com.echo.mapper.FriendshipMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.ChatGroup;
import com.echo.pojo.ChatGroupInvitation;
import com.echo.pojo.ChatGroupMember;
import com.echo.pojo.ChatGroupMessage;
import com.echo.pojo.Friendship;
import com.echo.vo.Result;
import com.echo.websocket.WsEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群聊：建群/成员/消息/未读。群消息投递复用 WsEventPublisher（跨实例 pub/sub）。
 */
@Service
public class GroupService {

    @Autowired
    private ChatGroupMapper groupMapper;

    @Autowired
    private ChatGroupMemberMapper memberMapper;

    @Autowired
    private ChatGroupMessageMapper messageMapper;

    @Autowired
    private ChatGroupInvitationMapper invitationMapper;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WsEventPublisher wsEventPublisher;

    @Autowired
    private NotificationService notificationService;

    public Result<Object> createGroup(Long ownerId, String name, List<Long> memberIds) {
        if (!StringUtils.hasText(name)) return Result.fail("群名称不能为空");
        if (ownerId == null) return Result.fail("未登录");
        ChatGroup g = new ChatGroup();
        g.setName(name.trim());
        g.setOwnerId(ownerId);
        g.setJoinVerificationEnabled(true);
        g.setCreatedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(g);
        addMemberRow(g.getId(), ownerId);
        if (memberIds != null) {
            for (Long uid : memberIds) {
                if (uid != null && !uid.equals(ownerId)) addMemberRow(g.getId(), uid);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", g.getId());
        data.put("name", g.getName());
        return Result.success(data);
    }

    public boolean isMember(Long groupId, Long userId) {
        if (groupId == null || userId == null) return false;
        return memberMapper.selectCount(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId)) > 0;
    }

    public Result<Object> listMyGroups(Long userId) {
        List<ChatGroupMember> myMemberships = memberMapper.selectList(
                new QueryWrapper<ChatGroupMember>().eq("user_id", userId));
        if (myMemberships.isEmpty()) return Result.success(List.of());

        Set<Long> groupIds = myMemberships.stream().map(ChatGroupMember::getGroupId).collect(Collectors.toSet());
        Map<Long, ChatGroupMember> memberByGroup = myMemberships.stream()
                .collect(Collectors.toMap(ChatGroupMember::getGroupId, m -> m, (a, b) -> a));
        List<ChatGroup> groups = groupMapper.selectList(new QueryWrapper<ChatGroup>()
                .in("id", groupIds).orderByDesc("updated_at"));

        List<Map<String, Object>> list = groups.stream().map(g -> {
            Map<String, Object> map = new HashMap<>();
            ChatGroupMember m = memberByGroup.get(g.getId());
            map.put("groupId", g.getId());
            map.put("name", g.getName());
            map.put("ownerId", g.getOwnerId());
            map.put("joinVerificationEnabled", !Boolean.FALSE.equals(g.getJoinVerificationEnabled()));
            map.put("createdAt", g.getCreatedAt());
            map.put("updatedAt", g.getUpdatedAt());
            map.put("remark", m == null ? null : m.getRemark());
            map.put("displayName", StringUtils.hasText(m == null ? null : m.getRemark())
                    ? m.getRemark() : g.getName());
            map.put("isArchived", m != null && Boolean.TRUE.equals(m.getIsArchived()));
            map.put("isPinned", m == null || !Boolean.FALSE.equals(m.getIsPinned()));
            ChatGroupMessage last = null;
            if (g.getLastMessageId() != null) {
                last = messageMapper.selectById(g.getLastMessageId());
                if (last != null && m != null && m.getHistoryClearedAt() != null
                        && !last.getCreatedAt().isAfter(m.getHistoryClearedAt())) {
                    last = null;
                }
            }
            map.put("lastMessage", last);
            long unread = 0;
            if (g.getLastMessageId() != null && last != null) {
                long lastId = g.getLastMessageId();
                long read = m != null && m.getLastReadMessageId() != null ? m.getLastReadMessageId() : 0;
                unread = Math.max(0, lastId - read);
            }
            map.put("unreadCount", unread);
            return map;
        }).collect(Collectors.toList());
        return Result.success(list);
    }

    public Result<Object> getGroup(Long groupId) {
        ChatGroup g = groupMapper.selectById(groupId);
        if (g == null) return Result.fail("群不存在");
        Map<String, Object> map = new HashMap<>();
        map.put("groupId", g.getId());
        map.put("name", g.getName());
        map.put("ownerId", g.getOwnerId());
        map.put("joinVerificationEnabled", !Boolean.FALSE.equals(g.getJoinVerificationEnabled()));
        map.put("members", getMemberDetails(groupId));
        return Result.success(map);
    }

    public Result<Object> addMember(Long groupId, Long operatorId, Long userId) {
        ChatGroup g = groupMapper.selectById(groupId);
        if (g == null) return Result.fail("群不存在");
        if (!g.getOwnerId().equals(operatorId)) return Result.fail("仅群主可添加成员");
        if (userId == null) return Result.fail("参数缺失");
        if (isMember(groupId, userId)) return Result.fail("已在群中");
        addMemberRow(groupId, userId);
        return Result.success("已添加");
    }

    /** 群主向自己的好友发邀请；接受前不会进入群。 */
    public Result<Object> inviteMember(Long groupId, Long inviterId, Long inviteeId) {
        ChatGroup group = groupMapper.selectById(groupId);
        if (group == null) return Result.fail("群不存在");
        if (!isMember(groupId, inviterId)) return Result.fail("仅群成员可邀请人员");
        if (inviteeId == null || userMapper.selectById(inviteeId) == null) return Result.fail("用户不存在");
        if (isMember(groupId, inviteeId)) return Result.fail("对方已在群中");
        boolean isFriend = friendshipMapper.selectCount(new QueryWrapper<Friendship>()
                .eq("user_id", inviterId).eq("friend_id", inviteeId).eq("status", 1)) > 0;
        if (!isFriend) return Result.fail("仅可邀请好友");

        if (Boolean.FALSE.equals(group.getJoinVerificationEnabled())) {
            addMemberRow(groupId, inviteeId);
            notificationService.notify(inviteeId, "GROUP_INVITE_AUTO_JOIN", "已加入群聊",
                    "你已被邀请加入群聊「" + group.getName() + "」，无需验证即可进入", groupId);
            return Result.success(Map.of("status", "ACCEPTED", "verified", false));
        }

        ChatGroupInvitation invitation = invitationMapper.selectOne(new QueryWrapper<ChatGroupInvitation>()
                .eq("group_id", groupId).eq("invitee_id", inviteeId));
        LocalDateTime now = LocalDateTime.now();
        if (invitation == null) {
            invitation = new ChatGroupInvitation();
            invitation.setGroupId(groupId);
            invitation.setInviterId(inviterId);
            invitation.setInviteeId(inviteeId);
            invitation.setStatus("PENDING");
            invitation.setCreatedAt(now);
            invitationMapper.insert(invitation);
        } else if ("PENDING".equals(invitation.getStatus())) {
            return Result.fail("邀请已发送，请等待对方处理");
        } else {
            invitation.setInviterId(inviterId);
            invitation.setStatus("PENDING");
            invitation.setCreatedAt(now);
            invitation.setRespondedAt(null);
            invitationMapper.updateById(invitation);
        }
        notificationService.notify(inviteeId, "GROUP_INVITE", "群聊邀请",
                "邀请你加入群聊「" + group.getName() + "」", invitation.getId());
        return Result.success(Map.of("invitationId", invitation.getId(), "status", "PENDING"));
    }

    public Result<Object> listMyInvitations(Long userId) {
        List<ChatGroupInvitation> invitations = invitationMapper.selectList(new QueryWrapper<ChatGroupInvitation>()
                .eq("invitee_id", userId).eq("status", "PENDING").orderByDesc("created_at"));
        List<Map<String, Object>> data = invitations.stream().map(invitation -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", invitation.getId());
            item.put("groupId", invitation.getGroupId());
            ChatGroup group = groupMapper.selectById(invitation.getGroupId());
            item.put("groupName", group != null ? group.getName() : "已删除的群");
            com.echo.pojo.User inviter = userMapper.selectById(invitation.getInviterId());
            item.put("inviterName", inviter != null ? inviter.getNickname() : "群主");
            item.put("createdAt", invitation.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
        return Result.success(data);
    }

    public Result<Object> respondInvitation(Long invitationId, Long userId, String action) {
        if (!"ACCEPT".equals(action) && !"REJECT".equals(action)) return Result.fail("无效操作");
        ChatGroupInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !userId.equals(invitation.getInviteeId())) return Result.fail("邀请不存在或无权限");
        if (!"PENDING".equals(invitation.getStatus())) return Result.fail("邀请已处理");
        ChatGroup group = groupMapper.selectById(invitation.getGroupId());
        if (group == null) return Result.fail("群不存在");
        invitation.setStatus("ACCEPT".equals(action) ? "ACCEPTED" : "REJECTED");
        invitation.setRespondedAt(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        if ("ACCEPT".equals(action) && !isMember(group.getId(), userId)) addMemberRow(group.getId(), userId);
        notificationService.notify(group.getOwnerId(), "GROUP_INVITE_RESULT", "群邀请" ,
                "ACCEPT".equals(action) ? "对方已加入群聊「" + group.getName() + "」" : "对方拒绝加入群聊「" + group.getName() + "」", invitation.getId());
        return Result.success("ACCEPT".equals(action) ? "已加入群聊" : "已拒绝邀请");
    }

    public Result<Object> removeMember(Long groupId, Long operatorId, Long userId) {
        ChatGroup g = groupMapper.selectById(groupId);
        if (g == null) return Result.fail("群不存在");
        if (g.getOwnerId().equals(userId)) return Result.fail("不能移除群主");
        if (!g.getOwnerId().equals(operatorId) && !operatorId.equals(userId)) return Result.fail("无权限");
        memberMapper.delete(new QueryWrapper<ChatGroupMember>().eq("group_id", groupId).eq("user_id", userId));
        return Result.success("已移除");
    }

    public Result<Object> leaveGroup(Long groupId, Long userId) {
        ChatGroup group = groupMapper.selectById(groupId);
        if (group == null || !isMember(groupId, userId)) return Result.fail("群不存在或非成员");
        if (group.getOwnerId().equals(userId)) return Result.fail("群主不能直接退出，请先转让群主或解散群聊");
        memberMapper.delete(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId));
        return Result.success("已退出群聊");
    }

    public Result<Object> updateJoinVerification(Long groupId, Long userId, Boolean enabled) {
        ChatGroup group = groupMapper.selectById(groupId);
        if (group == null) return Result.fail("群不存在");
        if (!group.getOwnerId().equals(userId)) return Result.fail("仅群主可修改入群验证");
        group.setJoinVerificationEnabled(enabled == null || enabled);
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);
        return Result.success(group.getJoinVerificationEnabled());
    }

    public Result<Object> getMessages(Long groupId, Long userId, String beforeTime, Integer limit) {
        QueryWrapper<ChatGroupMessage> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId);
        ChatGroupMember member = userId == null ? null : memberMapper.selectOne(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId));
        if (member != null && member.getHistoryClearedAt() != null) {
            qw.gt("created_at", member.getHistoryClearedAt());
        }
        if (StringUtils.hasText(beforeTime)) qw.lt("created_at", beforeTime);
        qw.orderByDesc("created_at").last("LIMIT " + (limit != null ? Math.min(limit, 100) : 20));
        List<ChatGroupMessage> messages = messageMapper.selectList(qw);
        Collections.reverse(messages);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("hasMore", messages.size() >= (limit != null ? limit : 20));
        return Result.success(data);
    }

    public Result<Object> updateRemark(Long userId, Long groupId, String remark) {
        ChatGroupMember member = findMember(groupId, userId);
        if (member == null) return Result.fail("群不存在或非成员");
        String normalized = StringUtils.hasText(remark) ? remark.trim() : null;
        if (normalized != null && normalized.length() > 100) return Result.fail("群备注不能超过 100 个字符");
        member.setRemark(normalized);
        memberMapper.updateById(member);
        return Result.success("群备注已更新");
    }

    public Result<Object> setArchived(Long userId, Long groupId, boolean archived) {
        ChatGroupMember member = findMember(groupId, userId);
        if (member == null) return Result.fail("群不存在或非成员");
        member.setIsArchived(archived);
        if (archived) member.setLastReadMessageId(latestMessageId(groupId));
        memberMapper.updateById(member);
        return Result.success(archived ? "群聊已从消息列表移除" : "群聊已恢复");
    }

    public Result<Object> clearHistory(Long userId, Long groupId) {
        ChatGroupMember member = findMember(groupId, userId);
        if (member == null) return Result.fail("群不存在或非成员");
        LocalDateTime now = LocalDateTime.now();
        member.setHistoryClearedAt(now);
        member.setLastReadMessageId(latestMessageId(groupId));
        memberMapper.updateById(member);
        return Result.success("群聊记录已清空");
    }

    public Result<Object> setPinned(Long userId, Long groupId, boolean pinned) {
        ChatGroupMember member = findMember(groupId, userId);
        if (member == null) return Result.fail("群不存在或非成员");
        member.setIsPinned(pinned);
        memberMapper.updateById(member);
        return Result.success(pinned ? "群聊已置顶" : "群聊已取消置顶");
    }

    public Result<Object> markRead(Long userId, Long groupId) {
        ChatGroup g = groupMapper.selectById(groupId);
        if (g == null || !isMember(groupId, userId)) return Result.fail("群不存在或非成员");
        ChatGroupMember m = memberMapper.selectOne(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId));
        if (m != null) {
            Long latestId = latestMessageId(groupId);
            Long previousId = m.getLastReadMessageId();
            if (latestId != null && (previousId == null || latestId > previousId)) {
                m.setLastReadMessageId(latestId);
                memberMapper.updateById(m);
            }
        }
        return Result.success("已读");
    }

    /** 持久化群消息（幂等：同 sender+clientMessageId 返回既有 id）。 */
    public ChatGroupMessage persistMessage(Long groupId, Long senderId, String clientMessageId,
                                           String content, String messageType) {
        return persistMessage(groupId, senderId, clientMessageId, content, messageType, null, null, null);
    }

    /** 持久化群消息（含文件字段：文件消息的 url/name/size）。 */
    public ChatGroupMessage persistMessage(Long groupId, Long senderId, String clientMessageId,
                                           String content, String messageType,
                                           String fileUrl, String fileName, Long fileSize) {
        if (StringUtils.hasText(clientMessageId)) {
            ChatGroupMessage existing = messageMapper.selectOne(new QueryWrapper<ChatGroupMessage>()
                    .eq("sender_id", senderId).eq("client_message_id", clientMessageId));
            if (existing != null) return existing;
        }
        ChatGroup groupBeforeInsert = groupMapper.selectById(groupId);
        boolean firstMessage = groupBeforeInsert == null || groupBeforeInsert.getLastMessageId() == null;
        ChatGroupMessage msg = new ChatGroupMessage();
        msg.setGroupId(groupId);
        msg.setSenderId(senderId);
        msg.setClientMessageId(StringUtils.hasText(clientMessageId) ? clientMessageId : null);
        msg.setContent(content);
        msg.setMessageType(messageType);
        msg.setFileUrl(fileUrl);
        msg.setFileName(fileName);
        msg.setFileSize(fileSize);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);

        ChatGroup g = groupMapper.selectById(groupId);
        if (g != null) {
            g.setLastMessageId(msg.getId());
            g.setUpdatedAt(LocalDateTime.now());
            groupMapper.updateById(g);
        }
        List<ChatGroupMember> members = memberMapper.selectList(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId));
        for (ChatGroupMember member : members) {
            member.setIsArchived(false);
            if (firstMessage) member.setIsPinned(true);
            memberMapper.updateById(member);
        }
        return msg;
    }

    /** 向群全部成员广播（跨实例经 Redis pub/sub）。 */
    public void broadcastToMembers(Long groupId, String json) {
        List<ChatGroupMember> members = memberMapper.selectList(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId));
        for (ChatGroupMember m : members) {
            if (wsEventPublisher != null) {
                wsEventPublisher.publishToUser(m.getUserId(), json);
            }
        }
    }

    private void addMemberRow(Long groupId, Long userId) {
        ChatGroupMember m = new ChatGroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(m);
    }

    private List<Map<String, Object>> getMemberDetails(Long groupId) {
        List<ChatGroupMember> rows = memberMapper.selectList(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatGroupMember m : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", m.getUserId());
            com.echo.pojo.User u = userMapper.selectById(m.getUserId());
            if (u != null) {
                map.put("nickname", u.getNickname());
                map.put("avatar", u.getAvatarUrl());
            }
            map.put("joinedAt", m.getJoinedAt());
            list.add(map);
        }
        return list;
    }

    private Long latestMessageId(Long groupId) {
        List<ChatGroupMessage> rows = messageMapper.selectList(new QueryWrapper<ChatGroupMessage>()
                .eq("group_id", groupId).orderByDesc("id").last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).getId();
    }

    private ChatGroupMember findMember(Long groupId, Long userId) {
        if (groupId == null || userId == null) return null;
        return memberMapper.selectOne(new QueryWrapper<ChatGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId));
    }
}

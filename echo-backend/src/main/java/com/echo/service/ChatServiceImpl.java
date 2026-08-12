package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.echo.file.FileService;
import com.echo.mapper.ConversationMapper;
import com.echo.mapper.MessageMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.Conversation;
import com.echo.pojo.Friendship;
import com.echo.pojo.Message;
import com.echo.pojo.User;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl extends ServiceImpl<MessageMapper, Message> implements ChatService {

    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private com.echo.mapper.FriendshipMapper friendshipMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private com.echo.ai.BotUserService botUserService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private PresenceService presenceService;

    @Value("${server.port}")
    private String serverPort;

    @Override
    public Result<Object> getMessages(Long currentUserId, Long friendId, String beforeTime, Integer limit) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        // (sender = me AND receiver = friend AND 我未删) OR (sender = friend AND receiver = me AND 我未删)
        queryWrapper.and(wrapper -> wrapper
                .nested(w -> w.eq("sender_id", currentUserId).eq("receiver_id", friendId).eq("deleted_by_sender", false))
                .or()
                .nested(w -> w.eq("sender_id", friendId).eq("receiver_id", currentUserId).eq("deleted_by_receiver", false))
        );
        
        if (StringUtils.hasText(beforeTime)) {
            queryWrapper.lt("created_at", beforeTime);
        }
        
        queryWrapper.orderByDesc("created_at");
        queryWrapper.last("LIMIT " + (limit != null ? limit : 20));
        
        List<Message> messages = messageMapper.selectList(queryWrapper);
        // 按时间正序排列返回
        Collections.reverse(messages);
        
        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("hasMore", messages.size() >= (limit != null ? limit : 20));
        
        return Result.success(data);
    }

    @Override
    public Result<Object> getConversations(Long currentUserId, Integer page, Integer size) {
        // Conversation表存储的是 user1_id, user2_id。需要查找 user1=me OR user2=me
        // 但这样设计有个问题：unread_count 是针对谁的？
        // 如果是共享记录，unread_count 必须分开存。
        // SQL里的 unread_count 是单个字段。这意味着Conversation表可能是单向的？
        // 还是说 unread_count 是针对 user1 的？
        // 通常做法：Conversation表存两份（双向），或者 unread_count 放在别处。
        // 假设 Conversation 表存两份：(A, B) 和 (B, A)。
        // 这样 unread_count 就是针对 user1 的。
        
        Page<Conversation> pageParam = new Page<>(page, size);
        QueryWrapper<Conversation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user1_id", currentUserId)
                .and(wrapper -> wrapper.eq("is_archived", false).or().isNull("is_archived"));
        queryWrapper.orderByDesc("updated_at");
        
        IPage<Conversation> resultPage = conversationMapper.selectPage(pageParam, queryWrapper);

        // AI 助手会话由前端单独注入，这里过滤掉所有 BOT，避免混入普通好友会话。
        Long botId = botUserService != null ? botUserService.getBotUserId() : null;
        List<Conversation> records = resultPage.getRecords().stream()
                .filter(c -> {
                    if (botId != null && botId.equals(c.getUser2Id())) return false;
                    User target = userMapper.selectById(c.getUser2Id());
                    return target == null || !"BOT".equalsIgnoreCase(target.getRole());
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> list = records.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("conversationId", c.getId());
            map.put("friendId", c.getUser2Id());
            map.put("unreadCount", c.getUnreadCount());
            map.put("updatedAt", c.getUpdatedAt());
            map.put("isPinned", !Boolean.FALSE.equals(c.getIsPinned()));

            User friend = userMapper.selectById(c.getUser2Id());
            if (friend != null) {
                map.put("friendNickname", friend.getNickname());
                map.put("friendAvatar", friend.getAvatarUrl());
                Friendship friendship = friendshipMapper.selectOne(new QueryWrapper<Friendship>()
                        .eq("user_id", currentUserId)
                        .eq("friend_id", c.getUser2Id())
                        .eq("status", 1));
                String remark = friendship == null ? null : friendship.getRemark();
                map.put("remark", remark);
                map.put("displayName", StringUtils.hasText(remark) ? remark : friend.getNickname());
                map.put("contactType", "FRIEND");
                // 隐私：对方关闭「展示在线状态」时对他人隐藏
                boolean online = presenceService != null && presenceService.isOnline(c.getUser2Id())
                        && Boolean.TRUE.equals(friend.getShowOnlineStatus());
                map.put("online", online);
            } else {
                map.put("online", false);
            }
            
            if (c.getLastMessageId() != null) {
                Message msg = messageMapper.selectById(c.getLastMessageId());
                map.put("lastMessage", msg);
            }
            
            return map;
        }).collect(Collectors.toList());
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", resultPage.getTotal());
        
        return Result.success(data);
    }

    @Override
    public Result<Object> setConversationArchived(Long currentUserId, Long friendId, boolean archived) {
        if (currentUserId == null || friendId == null) return Result.fail("会话参数无效");
        Conversation conv = conversationMapper.selectOne(new QueryWrapper<Conversation>()
                .eq("user1_id", currentUserId)
                .eq("user2_id", friendId));
        if (conv == null) return Result.success(archived ? "会话已隐藏" : "会话已恢复");
        conv.setIsArchived(archived);
        if (archived) conv.setUnreadCount(0);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return Result.success(archived ? "会话已从列表移除" : "会话已恢复");
    }

    @Override
    public Result<Object> setConversationPinned(Long currentUserId, Long friendId, boolean pinned) {
        if (currentUserId == null || friendId == null) return Result.fail("会话参数无效");
        Conversation conv = conversationMapper.selectOne(new QueryWrapper<Conversation>()
                .eq("user1_id", currentUserId)
                .eq("user2_id", friendId));
        if (conv == null) return Result.success(pinned ? "会话已置顶" : "会话已取消置顶");
        conv.setIsPinned(pinned);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return Result.success(pinned ? "会话已置顶" : "会话已取消置顶");
    }

    @Override
    public Result<Object> uploadFile(MultipartFile file, Long receiverId, Long groupId) {
        if (groupId != null) return fileService.uploadSmallFileToGroup(resolveCurrentUserId(), file, groupId);
        return fileService.uploadSmallFile(resolveCurrentUserId(), file, receiverId);
    }

    private Long resolveCurrentUserId() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && StringUtils.hasText(auth.getName()) && !"anonymousUser".equals(auth.getName())) {
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", auth.getName()));
            return user == null ? null : user.getId();
        }
        return null;
    }

    @Override
    public Result<Object> deleteMessages(Long currentUserId, Long friendId, String deleteType, String beforeTime) {
        if ("ALL".equals(deleteType)) {
            // 软删除：只影响自己这一侧的视图。我发的标记 deleted_by_sender，对方发的标记 deleted_by_receiver。
            UpdateWrapper<Message> uw1 = new UpdateWrapper<>();
            uw1.eq("sender_id", currentUserId).eq("receiver_id", friendId).set("deleted_by_sender", true);
            messageMapper.update(null, uw1);

            UpdateWrapper<Message> uw2 = new UpdateWrapper<>();
            uw2.eq("sender_id", friendId).eq("receiver_id", currentUserId).set("deleted_by_receiver", true);
            messageMapper.update(null, uw2);

            // 重置本方向会话行（unread_count / last_message_id），对方行不受影响
            if (conversationService != null) {
                conversationService.clearConversation(currentUserId, friendId);
            }
            return Result.success("已清空");
        }
        return Result.success("删除成功");
    }
}

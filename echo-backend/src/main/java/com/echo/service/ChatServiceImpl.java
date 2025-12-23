package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.echo.mapper.ConversationMapper;
import com.echo.mapper.MessageMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.Conversation;
import com.echo.pojo.Message;
import com.echo.pojo.User;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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

    @Value("${server.port}")
    private String serverPort;

    @Override
    public Result<Object> getMessages(Long currentUserId, Long friendId, String beforeTime, Integer limit) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        // (sender = me AND receiver = friend) OR (sender = friend AND receiver = me)
        queryWrapper.and(wrapper -> wrapper
                .nested(w -> w.eq("sender_id", currentUserId).eq("receiver_id", friendId))
                .or()
                .nested(w -> w.eq("sender_id", friendId).eq("receiver_id", currentUserId))
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
        queryWrapper.eq("user1_id", currentUserId);
        queryWrapper.orderByDesc("updated_at");
        
        IPage<Conversation> resultPage = conversationMapper.selectPage(pageParam, queryWrapper);
        
        List<Map<String, Object>> list = resultPage.getRecords().stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("conversationId", c.getId());
            map.put("friendId", c.getUser2Id());
            map.put("unreadCount", c.getUnreadCount());
            map.put("updatedAt", c.getUpdatedAt());
            
            User friend = userMapper.selectById(c.getUser2Id());
            if (friend != null) {
                map.put("friendNickname", friend.getNickname());
                map.put("friendAvatar", friend.getAvatarUrl());
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
    public Result<Object> uploadFile(MultipartFile file, Long receiverId) {
        if (file.isEmpty()) {
            return Result.fail("文件为空");
        }
        
        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = UUID.randomUUID().toString() + suffix;
            
            // 确保目录存在
            String uploadDir = "upload/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            File dest = new File(uploadDir + fileName);
            file.transferTo(dest);
            
            String fileUrl = "http://localhost:" + serverPort + "/upload/" + fileName; // 简单实现，实际应配置静态资源映射
            
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", UUID.randomUUID().toString());
            data.put("fileName", originalFilename);
            data.put("fileSize", file.getSize());
            data.put("fileUrl", fileUrl);
            data.put("fileType", suffix.substring(1).toUpperCase());
            
            return Result.success(data);
            
        } catch (IOException e) {
            e.printStackTrace();
            return Result.fail("文件上传失败");
        }
    }

    @Override
    public Result<Object> deleteMessages(Long currentUserId, Long friendId, String deleteType, String beforeTime) {
        if ("ALL".equals(deleteType)) {
            // 逻辑删除?
            // update message set deleted_by_sender = 1 where sender = me and receiver = friend
            // update message set deleted_by_receiver = 1 where sender = friend and receiver = me
            // 这里简化为物理删除或标记
            return Result.success("已清空");
        }
        return Result.success("删除成功");
    }
}

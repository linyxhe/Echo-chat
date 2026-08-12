package com.echo.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.echo.mapper.NotificationMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.Notification;
import com.echo.pojo.User;
import com.echo.websocket.WsEventPublisher;
import com.echo.websocket.pojo.ResultMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统通知：持久化 + 在线用户经 Redis pub/sub 实时推送（离线登录拉取）。
 */
@Service
public class NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WsEventPublisher wsEventPublisher;

    /** 发一条通知给某用户（插行 + WS 推送 NOTIFICATION 帧）。 */
    public void notify(Long userId, String type, String title, String content, Long relatedId) {
        if (userId == null) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRelatedId(relatedId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        try {
            notificationMapper.insert(n);
        } catch (Exception e) {
            return;
        }

        ResultMessage rm = new ResultMessage();
        rm.setType("NOTIFICATION");
        Map<String, Object> data = new HashMap<>();
        data.put("id", n.getId());
        data.put("type", type);
        data.put("title", title);
        data.put("content", content);
        data.put("relatedId", relatedId);
        data.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        rm.setData(data);
        rm.setTimestamp(System.currentTimeMillis());
        if (wsEventPublisher != null) {
            wsEventPublisher.publishToUser(userId, JSON.toJSONString(rm));
        }
    }

    /** 通知全部真实用户（跳过 BOT）。 */
    public void notifyAll(String type, String title, String content) {
        List<User> users = userMapper.selectList(new QueryWrapper<User>().ne("role", "BOT"));
        for (User u : users) {
            notify(u.getId(), type, title, content, null);
        }
    }

    public List<Notification> list(Long userId, Integer limit) {
        return notificationMapper.selectList(new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT " + (limit != null ? Math.min(limit, 50) : 20)));
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", userId).eq("is_read", false));
    }

    public void markRead(Long id, Long userId) {
        notificationMapper.update(null, new UpdateWrapper<Notification>()
                .eq("id", id).eq("user_id", userId).set("is_read", true));
    }

    public void markAllRead(Long userId) {
        notificationMapper.update(null, new UpdateWrapper<Notification>()
                .eq("user_id", userId).eq("is_read", false).set("is_read", true));
    }
}

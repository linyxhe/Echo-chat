package com.echo.websocket;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 帧跨实例发布器：把 WS 推送发布到 Redis 频道，所有实例的订阅者（WsEventSubscriber）
 * 投递给本地该用户的存活会话。单实例时本实例订阅者同样收到并本地投递。
 * Redis 不可用时降级（实时推送丢失，DB 仍是离线真源，重连补拉兜底）。
 */
@Component
public class WsEventPublisher {

    public static final String CHANNEL = "echo:ws:push";

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public WsEventPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 向某用户投递（跨实例：所有实例上该用户的存活会话都收到）。 */
    public void publishToUser(Long userId, String frameJson) {
        if (userId == null || frameJson == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("target", userId);
            payload.put("frame", frameJson);
            redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(payload));
        } catch (Exception e) {
            // 降级：忽略
        }
    }

    /** 广播给所有在线用户（跨实例）。 */
    public void publishBroadcast(String frameJson) {
        if (frameJson == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("target", null);
            payload.put("frame", frameJson);
            redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(payload));
        } catch (Exception e) {
            // 降级：忽略
        }
    }
}

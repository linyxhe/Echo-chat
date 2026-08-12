package com.echo.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 订阅 echo:ws:push 频道：把发布者发来的 WS 帧投递给本实例的本地会话。
 * 目标用户在本实例无存活会话则忽略（该用户可能在其他实例，由其他实例投递）。
 */
@Component
public class WsEventSubscriber implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            Map<String, Object> payload = JSON.parseObject(body, new TypeReference<Map<String, Object>>() {});
            Object targetObj = payload.get("target");
            Object frameObj = payload.get("frame");
            if (frameObj == null) return;
            String frameJson = String.valueOf(frameObj);
            if (targetObj != null) {
                Long userId = Long.valueOf(String.valueOf(targetObj));
                ChatEndpoint.deliverToUser(userId, frameJson);
            } else {
                ChatEndpoint.deliverBroadcast(frameJson);
            }
        } catch (Exception e) {
            // 解析/投递失败忽略（DB 仍是离线真源）
        }
    }
}

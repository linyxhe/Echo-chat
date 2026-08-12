package com.echo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线状态账本（Redis ZSET presence:online）。
 *
 * <p>member = userId，score = 最近心跳 epoch millis。ZSET 方便统计在线数（ZCARD）；
 * 陈旧成员由 {@link #sweepStale()} 定时清理，{@link #isOnline} 也会按 TTL 兜底判断。
 * 内存中的 {@code ChatEndpoint.onlineUsers} 仍是 WS 路由表（需要 Session 对象），
 * Redis 只是可查询的存在状态，两者在 WS 连接/关闭/心跳时同步更新。</p>
 *
 * <p>Redis 不可用时全部操作降级（isOnline=false / getOnlineCount=0），不影响登录、聊天与文件功能。</p>
 */
@Service
public class PresenceService {

    private static final String ONLINE_ZSET = "presence:online";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.presence.online-ttl-seconds:150}")
    private long onlineTtlSeconds;

    @Autowired
    public PresenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 标记用户在线（覆盖此前 score；客户端心跳会反复刷新以续期）。 */
    public void markOnline(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.opsForZSet().add(ONLINE_ZSET, String.valueOf(userId), System.currentTimeMillis());
        } catch (Exception e) {
            // Redis 不可用：降级，在线状态不可用
        }
    }

    /** 心跳续期。 */
    public void refresh(Long userId) {
        markOnline(userId);
    }

    /** 标记用户离线。 */
    public void markOffline(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.opsForZSet().remove(ONLINE_ZSET, String.valueOf(userId));
        } catch (Exception e) {
            // 忽略
        }
    }

    /** 用户当前是否在线（score 在 TTL 窗口内）。 */
    public boolean isOnline(Long userId) {
        if (userId == null) return false;
        try {
            Double score = redisTemplate.opsForZSet().score(ONLINE_ZSET, String.valueOf(userId));
            return score != null && (System.currentTimeMillis() - score) < onlineTtlSeconds * 1000;
        } catch (Exception e) {
            return false;
        }
    }

    /** 在线用户数（近似，含尚未清扫但仍在 TTL 内的成员）。 */
    public long getOnlineCount() {
        try {
            Long count = redisTemplate.opsForZSet().zCard(ONLINE_ZSET);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 清除超过 TTL 的陈旧成员，由定时清扫任务调用。 */
    public void sweepStale() {
        try {
            long cutoff = System.currentTimeMillis() - onlineTtlSeconds * 1000;
            redisTemplate.opsForZSet().removeRangeByScore(ONLINE_ZSET, Double.NEGATIVE_INFINITY, cutoff);
        } catch (Exception e) {
            // 忽略
        }
    }
}

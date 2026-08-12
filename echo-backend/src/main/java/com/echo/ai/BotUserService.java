package com.echo.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 解析并缓存 "AI 助手" 系统用户的身份。
 *
 * <p>bot 用户用普通自增 id、按 username 解析（避免显式高 id 顶爆 AUTO_INCREMENT：InnoDB 不允许
 * 把计数器重置到低于当前 max(id)，显式插入 900000001 会让下一个真实用户拿到 900000002）。</p>
 */
@Service
public class BotUserService {

    private final UserMapper userMapper;

    @Value("${app.ai.bot-username:ai_assistant}")
    private String botUsername;

    private volatile User cachedBot;

    @Autowired
    public BotUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 获取 bot 用户（懒加载并缓存）。未找到返回 null。 */
    public User getBotUser() {
        if (cachedBot == null) {
            synchronized (this) {
                if (cachedBot == null) {
                    cachedBot = userMapper.selectOne(new QueryWrapper<User>().eq("username", botUsername));
                }
            }
        }
        return cachedBot;
    }

    public Long getBotUserId() {
        User bot = getBotUser();
        return bot == null ? null : bot.getId();
    }

    public String getBotNickname() {
        User bot = getBotUser();
        return bot == null ? "AI 助手" : bot.getNickname();
    }

    public String getBotAvatar() {
        User bot = getBotUser();
        return bot == null ? null : bot.getAvatarUrl();
    }

    /** 查询任意 BOT 用户（系统助手或用户自定义助手）。 */
    public User getBotUserById(Long userId) {
        if (userId == null) return null;
        return userMapper.selectOne(new QueryWrapper<User>()
                .eq("id", userId)
                .eq("role", "BOT"));
    }

    public boolean isBotUserId(Long userId) {
        return getBotUserById(userId) != null;
    }
}

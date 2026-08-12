package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.echo.mapper.UserMapper;
import com.echo.pojo.User;
import com.echo.utils.JwtUtil;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JavaMailSender mailSender;
    
    @Value("${spring.mail.username}")
    private String mailFrom;

    @Override
    public Result<Object> register(User user, String captcha) {
        // 验证验证码
        String cachedCaptcha = redisTemplate.opsForValue().get("captcha:" + user.getEmail());
        if (cachedCaptcha == null || !cachedCaptcha.equals(captcha)) {
            return Result.fail("验证码错误或已过期");
        }

        // 检查用户名是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());
        if (userMapper.exists(queryWrapper)) {
            return Result.fail("用户名已存在");
        }

        // 检查邮箱是否已存在
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", user.getEmail());
        if (userMapper.exists(queryWrapper)) {
            return Result.fail("邮箱已存在");
        }

        // 密码加密
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPasswordHash(hashedPassword);
        user.setEmailVerified(true); // 既然通过验证码验证了，就标记为已验证
        user.setStatus(1); // 正常状态
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        // 保存用户
        save(user);

        // 删除验证码
        redisTemplate.delete("captcha:" + user.getEmail());
        
        // 生成Token并返回
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("token", token);

        return Result.success("注册成功", data);
    }

    @Override
    public Result<Object> login(String username, String password) {
        // 根据用户名或邮箱查找用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).or().eq("email", username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return Result.fail("用户名或密码错误");
        }
        
        if (user.getStatus() == 0) {
            return Result.fail("账号已被禁用");
        }
        if (user.getStatus() == 2) {
            return Result.fail("账号已被封禁");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        updateById(user);

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        long expireTime = System.currentTimeMillis() + 86400000; // 24小时

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatarUrl());
        data.put("email", user.getEmail());
        data.put("token", token);
        data.put("expireTime", expireTime);

        return Result.success("登录成功", data);
    }

    @Override
    public Result<Object> getProfile(Long userId) {
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        user.setPasswordHash(null); // 不返回密码
        return Result.success(user);
    }

    @Override
    public Result<Object> updateProfile(Long userId, User user) {
        User existingUser = getById(userId);
        if (existingUser == null) {
            return Result.fail("用户不存在");
        }

        if (StringUtils.hasText(user.getNickname())) {
            existingUser.setNickname(user.getNickname());
        }
        if (StringUtils.hasText(user.getAvatarUrl())) {
            existingUser.setAvatarUrl(user.getAvatarUrl());
        }
        // 不允许随意修改邮箱，除非走修改邮箱流程

        // 隐私开关（Boolean 不能走 hasText，需 null 守卫）
        if (user.getShowOnlineStatus() != null) {
            existingUser.setShowOnlineStatus(user.getShowOnlineStatus());
        }
        if (user.getShowReadReceipts() != null) {
            existingUser.setShowReadReceipts(user.getShowReadReceipts());
        }

        existingUser.setUpdatedAt(LocalDateTime.now());
        updateById(existingUser);

        return Result.success("更新成功");
    }

    @Override
    public Result<Object> updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return Result.fail("旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        updateById(user);

        return Result.success("密码修改成功");
    }

    @Override
    public User findByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public Result<Object> sendCaptcha(String email, String type) {
        // 生成6位验证码
        String captcha = String.valueOf(new Random().nextInt(899999) + 100000);
        
        // 存入Redis，5分钟有效
        redisTemplate.opsForValue().set("captcha:" + email, captcha, 5, TimeUnit.MINUTES);
        
        // 发送邮件
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Echo聊天验证码");
        message.setText("您的验证码是：" + captcha + "，有效期5分钟。");
        
        try {
            mailSender.send(message);
            return Result.success("验证码发送成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("验证码发送失败: " + e.getMessage());
        }
    }
}

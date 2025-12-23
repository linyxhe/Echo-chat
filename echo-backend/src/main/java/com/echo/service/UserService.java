package com.echo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.echo.pojo.User;
import com.echo.vo.Result;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {
    
    /**
     * 注册
     * @param user 用户信息
     * @return Result
     */
    Result<Object> register(User user, String captcha);
    
    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return Result
     */
    Result<Object> login(String username, String password);
    
    /**
     * 获取用户信息
     * @param userId 用户ID
     * @return Result
     */
    Result<Object> getProfile(Long userId);
    
    /**
     * 更新用户信息
     * @param userId 用户ID
     * @param user 用户信息
     * @return Result
     */
    Result<Object> updateProfile(Long userId, User user);
    
    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return Result
     */
    Result<Object> updatePassword(Long userId, String oldPassword, String newPassword);
    
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return User
     */
    User findByUsername(String username);

    /**
     * 发送邮箱验证码
     * @param email 邮箱
     * @param type 类型
     * @return Result
     */
    Result<Object> sendCaptcha(String email, String type);
}

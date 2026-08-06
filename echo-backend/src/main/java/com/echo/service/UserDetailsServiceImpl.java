package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * UserDetailsService实现类，用于加载用户详情
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 根据用户名查找用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);
        
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if (user.getStatus() == 0) {
            throw new org.springframework.security.authentication.DisabledException("账号已被禁用");
        }
        if (user.getStatus() == 2) {
            throw new org.springframework.security.authentication.LockedException("账号已被封禁");
        }

        // 创建UserDetails对象，用于Spring Security认证
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(), // 使用数据库中的密码哈希值
                new ArrayList<>() // 用户权限列表，当前版本简单实现，不包含具体权限
        );
    }
}

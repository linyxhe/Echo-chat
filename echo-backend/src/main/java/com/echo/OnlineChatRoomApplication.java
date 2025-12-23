package com.echo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@SpringBootApplication
public class OnlineChatRoomApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineChatRoomApplication.class, args);
    }

//    @Bean
//    public CommandLineRunner initDefaultAdmin(UserMapper userMapper, PasswordEncoder passwordEncoder) {
//        return args -> {
//            User existingAdmin = userMapper.selectOne(new QueryWrapper<User>().eq("role", "ADMIN").last("limit 1"));
//            if (existingAdmin != null) return;
//
//            User admin = new User();
//            admin.setUsername("admin");
//            admin.setNickname("管理员");
//            admin.setEmail("admin@qq.com");
//            admin.setEmailVerified(true);
//            admin.setStatus(1);
//            admin.setRole("ADMIN");
//            admin.setPasswordHash(passwordEncoder.encode("admin123456"));
//
//            LocalDateTime now = LocalDateTime.now();
//            admin.setCreatedAt(now);
//            admin.setUpdatedAt(now);
//
//            if (userMapper.exists(new QueryWrapper<User>().eq("username", admin.getUsername()))) {
//                admin.setUsername("admin" + System.currentTimeMillis());
//            }
//            if (userMapper.exists(new QueryWrapper<User>().eq("email", admin.getEmail()))) {
//                admin.setEmail("admin" + System.currentTimeMillis() + "@qq.com");
//            }
//
//            userMapper.insert(admin);
//        };
//    }
}

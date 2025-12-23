package com.echo.controller;

import com.echo.pojo.User;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<Object> register(@RequestBody Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String password = (String) payload.get("password");
        String nickname = (String) payload.get("nickname");
        String email = (String) payload.get("email");
        String captcha = (String) payload.get("captcha");

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname);
        user.setEmail(email);

        return userService.register(user, captcha);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<Object> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        return userService.login(username, password);
    }

    /**
     * 发送验证码
     */
    @PostMapping("/captcha/send")
    public Result<Object> sendCaptcha(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String type = payload.get("type");
        return userService.sendCaptcha(email, type);
    }
}

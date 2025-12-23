package com.echo.config;

import com.echo.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器，用于验证请求中的JWT令牌
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 从请求头中获取Authorization
        String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        // 检查Authorization头是否包含Bearer令牌
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            // 从JWT令牌中提取用户名
            username = jwtUtil.extractUsername(jwt);
        }

        // 如果用户名不为空且SecurityContext中没有认证信息
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 加载用户详情
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // 验证JWT令牌
            if (jwtUtil.validateToken(jwt, userDetails)) {
                // 创建认证令牌
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 设置认证信息到SecurityContext
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            }
        }

        // 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}
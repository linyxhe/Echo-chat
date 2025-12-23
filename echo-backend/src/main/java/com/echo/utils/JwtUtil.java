package com.echo.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    /**
     * 生成JWT token
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT token
     */
    public String generateToken(Long userId, String username) {
        // 设置token的过期时间
        Date expireDate = new Date(System.currentTimeMillis() + expiration);
        
        // 创建签名算法
        Algorithm algorithm = Algorithm.HMAC256(secret);
        
        // 创建JWT token
        return JWT.create()
                .withSubject(username) // 设置主题
                .withClaim("userId", userId) // 添加自定义声明
                .withIssuedAt(new Date()) // 设置签发时间
                .withExpiresAt(expireDate) // 设置过期时间
                .sign(algorithm); // 签名
    }
    
    /**
     * 验证并解析JWT token
     * @param token JWT token
     * @return DecodedJWT 对象，包含token中的所有声明
     */
    public DecodedJWT decodeToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            return null;
        }
    }
    
    /**
     * 从token中获取用户ID
     * @param token JWT token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        DecodedJWT decodedJWT = decodeToken(token);
        if (decodedJWT == null) {
            return null;
        }
        
        Claim userIdClaim = decodedJWT.getClaim("userId");
        return userIdClaim.asLong();
    }
    
    /**
     * 从token中获取用户名
     * @param token JWT token
     * @return 用户名
     */
    public String extractUsername(String token) {
        DecodedJWT decodedJWT = decodeToken(token);
        if (decodedJWT == null) {
            return null;
        }
        return decodedJWT.getSubject();
    }
    
    /**
     * 验证token是否有效
     * @param token JWT token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        DecodedJWT decodedJWT = decodeToken(token);
        return decodedJWT != null;
    }
    
    /**
     * 验证token是否有效（用于JwtAuthenticationFilter）
     */
    public boolean validateToken(String token, org.springframework.security.core.userdetails.UserDetails userDetails) {
        String username = extractUsername(token);
        return username != null && username.equals(userDetails.getUsername()) && validateToken(token);
    }
}

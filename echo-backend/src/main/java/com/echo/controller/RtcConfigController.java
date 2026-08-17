package com.echo.controller;

import com.echo.vo.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向已登录客户端下发 WebRTC ICE 配置。
 *
 * TURN 长期密码或 shared secret 只保存在后端环境中，不能写进网页或 APK。
 * 配置 shared secret 时按 coturn TURN REST 约定生成短期凭据；否则兼容静态账号。
 */
@RestController
@RequestMapping("/rtc")
public class RtcConfigController {

    @Value("${app.rtc.turn-urls:}")
    private String turnUrls;

    @Value("${app.rtc.turn-username:}")
    private String turnUsername;

    @Value("${app.rtc.turn-credential:}")
    private String turnCredential;

    @Value("${app.rtc.turn-shared-secret:}")
    private String turnSharedSecret;

    @Value("${app.rtc.credential-ttl-seconds:3600}")
    private long credentialTtlSeconds;

    @Value("${app.rtc.ice-transport-policy:all}")
    private String iceTransportPolicy;

    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig(Authentication authentication,
                                                  HttpServletResponse response) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        response.setHeader("Cache-Control", "no-store");

        List<Map<String, Object>> iceServers = new ArrayList<>();
        String credentialExpiresAt = null;
        List<String> urls = splitUrls(turnUrls);
        if (!urls.isEmpty()) {
            String username = trim(turnUsername);
            String credential = trim(turnCredential);

            if (!trim(turnSharedSecret).isEmpty()) {
                long ttl = Math.max(60, Math.min(credentialTtlSeconds, 86_400));
                long expiresAt = Instant.now().getEpochSecond() + ttl;
                String subject = authentication != null ? authentication.getName() : "echo";
                username = expiresAt + ":" + subject;
                credential = hmacSha1Base64(turnSharedSecret, username);
                credentialExpiresAt = Instant.ofEpochSecond(expiresAt).toString();
            }

            if (!username.isEmpty() && !credential.isEmpty()) {
                Map<String, Object> turn = new LinkedHashMap<>();
                turn.put("urls", urls);
                turn.put("username", username);
                turn.put("credential", credential);
                iceServers.add(turn);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("iceServers", iceServers);
        data.put("iceTransportPolicy", "relay".equalsIgnoreCase(iceTransportPolicy) ? "relay" : "all");
        data.put("turnConfigured", !iceServers.isEmpty());
        data.put("credentialExpiresAt", credentialExpiresAt);
        return Result.success(data);
    }

    private static List<String> splitUrls(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String url = item.trim();
            if (url.startsWith("turn:") || url.startsWith("turns:") || url.startsWith("stun:")) {
                result.add(url);
            }
        }
        return result;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String hmacSha1Base64(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(username.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("生成 TURN 临时凭据失败", e);
        }
    }
}

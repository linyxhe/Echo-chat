package com.echo.controller;

import com.echo.vo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtcConfigControllerTest {

    @Test
    void rejectsUnauthenticatedRtcConfigurationRequest() {
        RtcConfigController controller = controller("turn:turn.example.com:3478", "user", "pass", "");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.getConfig(null, new MockHttpServletResponse()));
    }

    @Test
    void returnsConfiguredStaticTurnServer() {
        RtcConfigController controller = controller(
                "turn:turn.example.com:3478?transport=udp,turn:turn.example.com:3478?transport=tcp",
                "echo-user",
                "echo-password",
                ""
        );

        Result<Map<String, Object>> result = controller.getConfig(
                new TestingAuthenticationToken("alice", null, "ROLE_USER"),
                new MockHttpServletResponse()
        );

        assertEquals(200, result.getCode());
        assertEquals(Boolean.TRUE, result.getData().get("turnConfigured"));
        List<?> servers = (List<?>) result.getData().get("iceServers");
        assertEquals(1, servers.size());
        Map<?, ?> turn = (Map<?, ?>) servers.get(0);
        assertEquals("echo-user", turn.get("username"));
        assertEquals("echo-password", turn.get("credential"));
        assertEquals(2, ((List<?>) turn.get("urls")).size());
        assertEquals(null, result.getData().get("credentialExpiresAt"));
    }

    @Test
    void sharedSecretGeneratesShortLivedCoturnCredential() {
        RtcConfigController controller = controller(
                "turn:turn.example.com:3478",
                "ignored-user",
                "ignored-password",
                "server-only-secret"
        );

        Result<Map<String, Object>> result = controller.getConfig(
                new TestingAuthenticationToken("alice", null, "ROLE_USER"),
                new MockHttpServletResponse()
        );

        Map<?, ?> turn = (Map<?, ?>) ((List<?>) result.getData().get("iceServers")).get(0);
        String username = String.valueOf(turn.get("username"));
        String credential = String.valueOf(turn.get("credential"));
        assertTrue(username.endsWith(":alice"));
        assertNotEquals("ignored-password", credential);
        assertTrue(credential.length() >= 20);
        String expiresAt = String.valueOf(result.getData().get("credentialExpiresAt"));
        assertTrue(expiresAt.endsWith("Z"));
    }

    private static RtcConfigController controller(String urls,
                                                  String username,
                                                  String credential,
                                                  String sharedSecret) {
        RtcConfigController controller = new RtcConfigController();
        ReflectionTestUtils.setField(controller, "turnUrls", urls);
        ReflectionTestUtils.setField(controller, "turnUsername", username);
        ReflectionTestUtils.setField(controller, "turnCredential", credential);
        ReflectionTestUtils.setField(controller, "turnSharedSecret", sharedSecret);
        ReflectionTestUtils.setField(controller, "credentialTtlSeconds", 3600L);
        ReflectionTestUtils.setField(controller, "iceTransportPolicy", "all");
        return controller;
    }
}

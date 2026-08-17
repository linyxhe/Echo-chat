package com.echo.websocket;

import com.echo.service.PresenceService;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatEndpointCallSignalTest {

    @AfterEach
    void resetStatics() {
        ReflectionTestUtils.setField(ChatEndpoint.class, "presenceService", null);
        ReflectionTestUtils.setField(ChatEndpoint.class, "wsEventPublisher", null);
    }

    @Test
    void offerToOfflineUserReturnsAckAndOfflineSignal() throws Exception {
        PresenceService presence = mock(PresenceService.class);
        when(presence.isOnline(2L)).thenReturn(false);
        ReflectionTestUtils.setField(ChatEndpoint.class, "presenceService", presence);

        Session session = openSession();
        ChatEndpoint endpoint = endpointFor(1L);
        String callId = System.currentTimeMillis() + "-offer-test";
        ReflectionTestUtils.invokeMethod(endpoint, "handleCallSignal", frame(2L, "OFFER", callId, sdp("offer")), session);

        ArgumentCaptor<String> frames = ArgumentCaptor.forClass(String.class);
        verify(session.getBasicRemote(), org.mockito.Mockito.times(2)).sendText(frames.capture());
        String combined = String.join("\n", frames.getAllValues());
        assertTrue(combined.contains("CALL_SIGNAL_ACK"));
        assertTrue(combined.contains("OFFLINE"));
        assertTrue(combined.contains(callId));
    }

    @Test
    void ringingIsForwardedWithoutAck() throws Exception {
        PresenceService presence = mock(PresenceService.class);
        WsEventPublisher publisher = mock(WsEventPublisher.class);
        when(presence.isOnline(2L)).thenReturn(true);
        when(publisher.publishToUser(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        ReflectionTestUtils.setField(ChatEndpoint.class, "presenceService", presence);
        ReflectionTestUtils.setField(ChatEndpoint.class, "wsEventPublisher", publisher);

        Session session = openSession();
        String callId = System.currentTimeMillis() + "-ringing-test";
        ReflectionTestUtils.invokeMethod(endpointFor(1L), "handleCallSignal", frame(2L, "RINGING", callId, null), session);

        verify(publisher).publishToUser(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.contains("RINGING"));
        verify(session.getBasicRemote(), never()).sendText(org.mockito.ArgumentMatchers.contains("CALL_SIGNAL_ACK"));
    }

    @Test
    void expiredOfferIsRejectedAsInvalid() throws Exception {
        Session session = openSession();
        String callId = (System.currentTimeMillis() - 10 * 60 * 1000L) + "-expired";
        ReflectionTestUtils.invokeMethod(endpointFor(1L), "handleCallSignal", frame(2L, "OFFER", callId, sdp("offer")), session);

        ArgumentCaptor<String> frame = ArgumentCaptor.forClass(String.class);
        verify(session.getBasicRemote()).sendText(frame.capture());
        assertTrue(frame.getValue().contains("CALL_SIGNAL_ACK"));
        assertTrue(frame.getValue().contains("INVALID"));
    }

    private static ChatEndpoint endpointFor(Long userId) {
        ChatEndpoint endpoint = new ChatEndpoint();
        ReflectionTestUtils.setField(endpoint, "userId", userId);
        return endpoint;
    }

    private static Session openSession() {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(remote);
        return session;
    }

    private static Map<String, Object> frame(Long toUserId, String kind, String callId, Object payload) {
        Map<String, Object> data = new HashMap<>();
        data.put("toUserId", toUserId);
        data.put("kind", kind);
        data.put("callId", callId);
        data.put("callType", "VOICE");
        data.put("payload", payload);
        return Map.of("data", data);
    }

    private static Map<String, Object> sdp(String type) {
        return Map.of("sdp", Map.of("type", type, "sdp", "v=0\r\n"));
    }
}

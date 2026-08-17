package com.echo.agent;

import java.time.LocalDateTime;

/** Deliberately small payload that can be shown to the owner before any write occurs. */
public record AgentConfirmationPayload(
        String token,
        String actionType,
        String summary,
        String preview,
        LocalDateTime expiresAt,
        Long botUserId
) { }

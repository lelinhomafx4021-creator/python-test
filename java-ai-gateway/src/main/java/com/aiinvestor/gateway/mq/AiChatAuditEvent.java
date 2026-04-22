package com.aiinvestor.gateway.mq;

import java.time.Instant;

public record AiChatAuditEvent(
        String traceId,
        String userId,
        String sessionId,
        String endpoint,
        String message,
        Instant createdAt
) {
}

package com.aiinvestor.gateway.modules.membership.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VipApplicationVO(
        Long id,
        Long userId,
        String username,
        BigDecimal paymentAmount,
        String paymentNote,
        String paymentProofUrl,
        String status,
        String rejectReason,
        Long reviewedBy,
        String reviewedByUsername,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.aiinvestor.gateway.modules.membership.vo;

public record VipApplicationSubmitVO(
        Long id,
        String status,
        String paymentProofUrl
) {
}

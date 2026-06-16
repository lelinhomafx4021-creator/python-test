package com.aiinvestor.gateway.modules.membership.vo;

/**
 * VIP 申请提交响应。
 *
 * @param id              申请 ID
 * @param status           审核状态（pending）
 * @param paymentProofUrl  付款凭证访问 URL
 */
public record VipApplicationSubmitVO(
        Long id,
        String status,
        String paymentProofUrl
) {
}

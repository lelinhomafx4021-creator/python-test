package com.aiinvestor.gateway.modules.membership.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP 申请详情视图（管理员审核列表使用）。
 *
 * @param id                 申请 ID
 * @param userId             申请人用户 ID
 * @param username           申请人用户名
 * @param paymentAmount      付款金额
 * @param paymentNote        付款备注
 * @param paymentProofUrl    付款凭证访问 URL
 * @param status             审核状态：pending / approved / rejected
 * @param rejectReason       驳回原因
 * @param reviewedBy         审核人用户 ID
 * @param reviewedByUsername 审核人用户名
 * @param reviewedAt         审核时间
 * @param createdAt          申请创建时间
 * @param updatedAt          最后更新时间
 */
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

package com.aiinvestor.gateway.modules.membership.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP 申请实体。
 * <p>
 * 记录用户提交的 VIP 付费申请，包含付款凭证和审核状态。
 */
@Data
@TableName("vip_applications")
public class VipApplicationDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人用户 ID。 */
    private Long userId;

    /** 申请人用户名。 */
    private String username;

    /** 付款金额。 */
    private BigDecimal paymentAmount;

    /** 付款截图凭证 URL。 */
    private String paymentScreenshot;

    /** 付款备注。 */
    private String paymentNote;

    /** 审核状态：pending（待审核）、approved（已通过）、rejected（已驳回）。 */
    private String status;

    /** 驳回原因（仅当 status=rejected 时有效）。 */
    private String rejectReason;

    /** 审核人用户 ID。 */
    private Long reviewedBy;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}

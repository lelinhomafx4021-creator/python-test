package com.aiinvestor.gateway.modules.membership.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * VIP 申请审核请求体。
 */
@Data
public class VipApplicationReviewRequest {

    /** 审核动作：approve（通过）/ reject（驳回），不能为空。 */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /** 驳回原因，仅当 action=reject 时必填。支持 JSON 字段名 reject_reason。 */
    @JsonAlias("reject_reason")
    private String rejectReason;
}

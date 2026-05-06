package com.aiinvestor.gateway.modules.membership.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VipApplicationReviewRequest {

    @NotBlank(message = "审核动作不能为空")
    private String action;

    @JsonAlias("reject_reason")
    private String rejectReason;
}

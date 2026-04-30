package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端修改会员方案请求。
 */
public class AdminUpdateMembershipRequest {

    /** 会员方案编码。 */
    @NotBlank(message = "会员方案不能为空")
    private String planCode;

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}

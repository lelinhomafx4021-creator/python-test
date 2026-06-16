package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端修改用户会员方案请求体。
 * <p>
 * 管理员可通过此请求将指定用户切换到新的会员方案（如 guest/normal/vip），
 * 系统会自动为方案内的各功能特性创建对应配额。
 */
public class AdminUpdateMembershipRequest {

    /** 目标会员方案编码（guest/normal/vip）。 */
    @NotBlank(message = "会员方案不能为空")
    private String planCode;

    /**
     * @return 会员方案编码
     */
    public String getPlanCode() {
        return planCode;
    }

    /**
     * @param planCode 会员方案编码
     */
    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}

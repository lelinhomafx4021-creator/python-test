package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端修改用户角色请求体。
 * <p>
 * 管理员可通过此请求将指定用户的角色变更为 guest/normal/vip/admin。
 */
public class AdminUpdateUserRoleRequest {

    /** 目标角色编码（guest/normal/vip/admin）。 */
    @NotBlank(message = "角色不能为空")
    private String role;

    /**
     * @return 目标角色编码
     */
    public String getRole() {
        return role;
    }

    /**
     * @param role 目标角色编码
     */
    public void setRole(String role) {
        this.role = role;
    }
}

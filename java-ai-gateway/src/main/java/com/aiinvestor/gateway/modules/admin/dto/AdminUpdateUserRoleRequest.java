package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端修改用户角色请求。
 */
public class AdminUpdateUserRoleRequest {

    /** 目标角色。 */
    @NotBlank(message = "角色不能为空")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

package com.aiinvestor.gateway.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ============================================================
 * 登录请求体 - 前端 POST /gateway/auth/login 时的 JSON 映射
 * ============================================================
 *
 * DTO（Data Transfer Object）的作用：
 *   专门用来承接前端/外部系统传来的数据。与数据库实体（DO）分开，
 *   避免前端直接修改数据库字段导致安全问题。
 *
 * @author AI Investor Team
 */
@Data
public class LoginRequest {

    /** 用户名，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文），不能为空。注意：传输层应走 HTTPS 保护密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}

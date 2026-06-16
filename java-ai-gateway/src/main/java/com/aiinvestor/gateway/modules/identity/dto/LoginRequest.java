package com.aiinvestor.gateway.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体 —— 前端 POST /gateway/auth/login 时的 JSON 映射。
 * <p>
 * DTO（Data Transfer Object）的职责：
 * 专门承接前端/外部系统传来的数据，与数据库实体（DO）严格分离。
 * 好处：避免前端直接修改数据库字段导致安全问题（Mass Assignment 攻击），
 * 同时支持独立的校验规则与字段裁剪。
 * <p>
 * 注意：密码以明文传输，生产环境必须走 HTTPS 信道保护。
 *
 * @author AI Investor Team
 */
@Data
public class LoginRequest {

    /**
     * 登录用户名，不能为空。由前端登录表单提交。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录密码（明文），不能为空。
     * 服务端收到后与数据库中 BCrypt 密文比对，不会以明文存储。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}

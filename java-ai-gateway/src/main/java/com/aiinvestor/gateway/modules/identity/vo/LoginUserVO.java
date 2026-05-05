package com.aiinvestor.gateway.modules.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录态返回的用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserVO {

    /**
     * 用户 ID。
     */
    private Long id;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 昵称。
     */
    private String nickname;

    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 用户角色。
     */
    private String role;

    /**
     * 登录 token。
     */
    private String token;
}

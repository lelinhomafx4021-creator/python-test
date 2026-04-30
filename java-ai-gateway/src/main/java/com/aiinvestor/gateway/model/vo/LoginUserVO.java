package com.aiinvestor.gateway.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后的用户信息。
 * 在原有 token 基础上补充昵称和角色，方便前端直接展示会员终端的身份状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserVO {

    /** 用户 ID。 */
    private Long id;

    /** 用户名。 */
    private String username;

    /** 用户昵称。 */
    private String nickname;

    /** 用户角色。 */
    private String role;

    /** 登录 token。 */
    private String token;
}

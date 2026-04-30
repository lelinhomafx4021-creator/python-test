package com.aiinvestor.gateway.modules.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 当前用户资料视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {

    /** 用户 ID。 */
    private Long id;

    /** 用户名。 */
    private String username;

    /** 昵称。 */
    private String nickname;

    /** 头像地址。 */
    private String avatarUrl;

    /** 手机号。 */
    private String phone;

    /** 角色。 */
    private String role;

    /** 状态。 */
    private Integer status;

    /** 最后登录时间。 */
    private LocalDateTime lastLoginAt;
}

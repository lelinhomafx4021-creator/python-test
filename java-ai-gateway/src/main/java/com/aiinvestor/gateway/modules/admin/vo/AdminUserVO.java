package com.aiinvestor.gateway.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户列表视图。
 */
@Data
public class AdminUserVO {

    /** 用户 ID。 */
    private Long id;

    /** 用户名。 */
    private String username;

    /** 昵称。 */
    private String nickname;

    /** 手机号。 */
    private String phone;

    /** 角色。 */
    private String role;

    /** 状态。 */
    private Integer status;

    /** 头像地址。 */
    private String avatarUrl;

    /** 当前会员方案。 */
    private String planCode;

    /** 会员状态。 */
    private String membershipStatus;

    /** AI 日额度。 */
    private Integer aiChatLimit;

    /** AI 已使用次数。 */
    private Integer aiChatUsed;

    /** 自选分组数。 */
    private Integer watchlistCount;

    /** 最后登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

package com.aiinvestor.gateway.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户列表视图。
 * <p>
 * 展示用户基本信息、角色状态、会员方案、AI 额度使用情况、
 * 自选分组数量等管理端关注的综合信息。
 */
@Data
public class AdminUserVO {

    /** 用户 ID。 */
    private Long id;

    /** 登录用户名。 */
    private String username;

    /** 用户昵称（展示用）。 */
    private String nickname;

    /** 手机号。 */
    private String phone;

    /** 角色：guest / normal / vip / admin。 */
    private String role;

    /** 账号状态：active / disabled。 */
    private String status;

    /** 头像 URL 地址。 */
    private String avatarUrl;

    /** 当前生效的会员方案编码。 */
    private String planCode;

    /** 会员状态：active / expired / cancelled。 */
    private String membershipStatus;

    /** AI 对话每日限额（次）。 */
    private Integer aiChatLimit;

    /** AI 对话当日已使用次数。 */
    private Integer aiChatUsed;

    /** 自选股分组数量。 */
    private Integer watchlistCount;

    /** 最近一次登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 注册时间。 */
    private LocalDateTime createdAt;
}

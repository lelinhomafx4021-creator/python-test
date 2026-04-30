package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体。
 * 这一版在原有登录能力上补齐了昵称、头像、角色和最后登录时间，
 * 为会员、自选和模拟交易这些主业务模块提供统一的用户主档。
 */
@Data
@TableName("users")
public class UserDO {

    /** 用户主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名。 */
    private String username;

    /** 旧密码字段，兼容历史数据。 */
    private String password;

    /** 新密码摘要字段，逐步替代旧密码列。 */
    private String passwordHash;

    /** 手机号。 */
    private String phone;

    /** 昵称。 */
    private String nickname;

    /** 头像地址。 */
    private String avatarUrl;

    /** 角色：guest/normal/vip/admin。 */
    private String role;

    /** 用户状态：1=正常，0=禁用。 */
    private Integer status;

    /** 最后登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

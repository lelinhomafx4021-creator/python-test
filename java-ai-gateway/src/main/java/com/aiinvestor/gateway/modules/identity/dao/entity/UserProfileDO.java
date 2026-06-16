package com.aiinvestor.gateway.modules.identity.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户扩展画像实体（DO = Data Object），对应数据库表 user_profiles。
 * <p>
 * 与用户主表（users）是一对一关系，存放与投资偏好相关的扩展字段。
 * 主表存身份信息（用户名、密码、昵称等），此表存投资画像（风险等级、投资年限等）。
 * <p>
 * 设计意图：将核心身份字段与投资偏好字段分离，降低耦合、便于扩展。
 *
 * @author AI Investor Team
 */
@Data
@TableName("user_profiles")
public class UserProfileDO {

    /**
     * 用户 ID，同时作为主键，与 users 表的 id 一一对应。
     * 使用 INPUT 策略表示 ID 由业务方（插入时）显式赋值，不自增。
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /**
     * 风险等级，枚举候选值如 conservative（保守）、balanced（平衡）、aggressive（进取）。
     * 默认值为 "balanced"。
     */
    private String riskLevel;

    /**
     * 投资年限（年），用于评估用户经验水平，辅助投研建议。
     */
    private Integer investmentYears;

    /**
     * 关注板块，多个板块用逗号分隔，如 "新能源,半导体,医药"。
     */
    private String interestedSectors;

    /**
     * 个人简介，用户自由填写的自我介绍文本，最长 255 字符。
     */
    private String bio;

    /**
     * 记录最后更新时间，每次修改画像时自动刷新。
     */
    private LocalDateTime updatedAt;
}

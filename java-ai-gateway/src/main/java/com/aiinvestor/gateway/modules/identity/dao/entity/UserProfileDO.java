package com.aiinvestor.gateway.modules.identity.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户扩展画像实体。
 */
@Data
@TableName("user_profiles")
public class UserProfileDO {

    /**
     * 用户 ID，同时也是主键。
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /**
     * 风险等级。
     */
    private String riskLevel;

    /**
     * 投资年限。
     */
    private Integer investmentYears;

    /**
     * 关注板块，逗号分隔。
     */
    private String interestedSectors;

    /**
     * 个人简介。
     */
    private String bio;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}

package com.aiinvestor.gateway.modules.membership.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户功能配额实体。
 */
@Data
@TableName("user_feature_quotas")
public class UserFeatureQuotaDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 功能编码。 */
    private String featureCode;

    /** 周期类型。 */
    private String periodType;

    /** 限额。 */
    private Integer limitCount;

    /** 已使用数量。 */
    private Integer usedCount;

    /** 下次重置时间。 */
    private LocalDateTime resetAt;
}

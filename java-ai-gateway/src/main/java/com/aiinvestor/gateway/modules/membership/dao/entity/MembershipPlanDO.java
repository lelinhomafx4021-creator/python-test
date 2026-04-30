package com.aiinvestor.gateway.modules.membership.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员方案实体。
 */
@Data
@TableName("membership_plans")
public class MembershipPlanDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案编码。 */
    private String planCode;

    /** 方案名称。 */
    private String planName;

    /** 价格。 */
    private BigDecimal price;

    /** 计费周期。 */
    private String billingCycle;

    /** 默认配额 JSON。 */
    private String quotaJson;

    /** 状态。 */
    private String status;
}

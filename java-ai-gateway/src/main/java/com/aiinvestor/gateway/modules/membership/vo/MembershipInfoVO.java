package com.aiinvestor.gateway.modules.membership.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 当前会员信息视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipInfoVO {

    /** 方案编码。 */
    private String planCode;

    /** 方案名称。 */
    private String planName;

    /** 价格。 */
    private BigDecimal price;

    /** 周期。 */
    private String billingCycle;

    /** 状态。 */
    private String status;

    /** 开始时间。 */
    private LocalDateTime startAt;

    /** 结束时间。 */
    private LocalDateTime endAt;
}

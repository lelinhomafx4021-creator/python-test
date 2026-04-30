package com.aiinvestor.gateway.modules.membership.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 功能配额视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureQuotaVO {

    /** 功能编码。 */
    private String featureCode;

    /** 周期类型。 */
    private String periodType;

    /** 限额。 */
    private Integer limitCount;

    /** 已用数量。 */
    private Integer usedCount;

    /** 下次重置时间。 */
    private LocalDateTime resetAt;
}

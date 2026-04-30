package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟账户视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperAccountVO {

    /** 账户 ID。 */
    private Long id;

    /** 账户号。 */
    private String accountNo;

    /** 可用现金。 */
    private BigDecimal cashBalance;

    /** 冻结现金。 */
    private BigDecimal frozenCash;

    /** 总资产。 */
    private BigDecimal totalAsset;

    /** 累计盈亏。 */
    private BigDecimal totalPnl;

    /** 状态。 */
    private String status;
}

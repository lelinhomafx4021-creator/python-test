package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟持仓视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperPositionVO {

    /** 持仓 ID。 */
    private Long id;

    /** 股票代码。 */
    private String symbol;

    /** 股票名称。 */
    private String name;

    /** 持仓数量。 */
    private Integer positionQty;

    /** 可卖数量。 */
    private Integer availableQty;

    /** 持仓成本。 */
    private BigDecimal avgCost;

    /** 市值。 */
    private BigDecimal marketValue;

    /** 浮动盈亏。 */
    private BigDecimal floatingPnl;

    /** 鏈€鏂颁环銆?*/
    private BigDecimal latestPrice;

    /** 娑ㄨ穼骞呫€?*/
    private BigDecimal changePercent;

    /** 娑ㄨ穼棰濄€?*/
    private BigDecimal changeAmount;

    /** 琛屾儏鏃堕棿銆?*/
    private LocalDateTime quoteTime;
}

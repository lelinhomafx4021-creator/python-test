package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}

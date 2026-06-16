package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟持仓视图对象 (VO)。
 * <p>
 * 返回给前端的单只股票持仓详情，包含数量、成本、市值、浮动盈亏，
 * 以及从行情服务获取的最新价、涨跌幅、涨跌额等实时行情数据。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperPositionVO {

    /** 持仓记录 ID */
    private Long id;

    /** 股票代码 */
    private String symbol;

    /** 股票名称 */
    private String name;

    /** 持仓数量（总持有股数） */
    private Integer positionQty;

    /** 可卖数量（已结算、可立即卖出的股数） */
    private Integer availableQty;

    /** 持仓均价（元），加权平均买入成本 */
    private BigDecimal avgCost;

    /** 持仓市值（元）= 持仓数量 * 最新价 */
    private BigDecimal marketValue;

    /** 浮动盈亏（元）= (最新价 - 持仓均价) * 持仓数量 */
    private BigDecimal floatingPnl;

    /** 最新行情价格（元） */
    private BigDecimal latestPrice;

    /** 涨跌幅（百分比） */
    private BigDecimal changePercent;

    /** 涨跌额（元） */
    private BigDecimal changeAmount;

    /** 行情数据更新时间 */
    private LocalDateTime quoteTime;
}

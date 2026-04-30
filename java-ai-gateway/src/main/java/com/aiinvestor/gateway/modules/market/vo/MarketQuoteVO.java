package com.aiinvestor.gateway.modules.market.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行情视图对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketQuoteVO {

    /** 股票代码。 */
    private String symbol;

    /** 股票名称。 */
    private String name;

    /** 最新价。 */
    private BigDecimal lastPrice;

    /** 涨跌幅。 */
    private BigDecimal changePercent;

    /** 涨跌额。 */
    private BigDecimal changeAmount;

    /** 最高价。 */
    private BigDecimal highPrice;

    /** 最低价。 */
    private BigDecimal lowPrice;

    /** 开盘价。 */
    private BigDecimal openPrice;

    /** 成交量。 */
    private BigDecimal volume;

    /** 成交额。 */
    private BigDecimal turnover;

    /** 换手率。 */
    private BigDecimal turnoverRate;

    /** 振幅。 */
    private BigDecimal amplitude;

    /** 行情时间。 */
    private LocalDateTime quoteTime;
}

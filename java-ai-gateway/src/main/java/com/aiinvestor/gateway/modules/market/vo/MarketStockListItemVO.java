package com.aiinvestor.gateway.modules.market.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 股票列表条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketStockListItemVO {

    /**
     * 股票代码。
     */
    private String symbol;

    /**
     * 股票名称。
     */
    private String name;

    /**
     * 最新价。
     */
    private BigDecimal lastPrice;

    /**
     * 涨跌幅。
     */
    private BigDecimal changePercent;

    /**
     * 涨跌额。
     */
    private BigDecimal changeAmount;

    /**
     * 成交量。
     */
    private BigDecimal volume;

    /**
     * 成交额。
     */
    private BigDecimal turnover;

    /**
     * 换手率。
     */
    private BigDecimal turnoverRate;

    /**
     * 最高价。
     */
    private BigDecimal highPrice;

    /**
     * 最低价。
     */
    private BigDecimal lowPrice;

    /**
     * 开盘价。
     */
    private BigDecimal openPrice;

    /**
     * 总市值。
     */
    private BigDecimal totalMarketValue;

    /**
     * 流通市值。
     */
    private BigDecimal circulatingMarketValue;

    /**
     * 60 日涨跌幅。
     */
    private BigDecimal sixtyDayChangePercent;

    /**
     * 年初至今涨跌幅。
     */
    private BigDecimal yearToDateChangePercent;
}

package com.aiinvestor.gateway.modules.market.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行情快照实体。
 */
@Data
@TableName("market_quotes")
public class MarketQuoteDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码。 */
    private String symbol;

    /** 最新价。 */
    private BigDecimal lastPrice;

    /** 涨跌幅。 */
    private BigDecimal changePct;

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

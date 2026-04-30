package com.aiinvestor.gateway.modules.watchlist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 自选股条目视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemVO {

    /** 条目 ID。 */
    private Long id;

    /** 股票代码。 */
    private String symbol;

    /** 股票名称。 */
    private String name;

    /** 备注。 */
    private String note;

    /** 是否开启提醒。 */
    private Boolean alertEnabled;

    /** 排序。 */
    private Integer sortOrder;

    /** 最新价。 */
    private BigDecimal lastPrice;

    /** 涨跌幅。 */
    private BigDecimal changePercent;
}

package com.aiinvestor.gateway.modules.market.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 股票列表分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketStockPageVO {

    /**
     * 当前页码。
     */
    private Integer page;

    /**
     * 每页数量。
     */
    private Integer pageSize;

    /**
     * 总条数。
     */
    private Integer total;

    /**
     * 当前页条目。
     */
    private List<MarketStockListItemVO> items;
}

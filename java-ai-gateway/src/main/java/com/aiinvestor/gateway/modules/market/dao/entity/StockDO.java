package com.aiinvestor.gateway.modules.market.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 股票主数据实体。
 */
@Data
@TableName("stocks")
public class StockDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码。 */
    private String symbol;

    /** 股票名称。 */
    private String name;

    /** 交易所。 */
    private String exchange;

    /** 市场。 */
    private String market;

    /** 板块编码。 */
    private String sectorCode;

    /** 状态。 */
    private String status;
}

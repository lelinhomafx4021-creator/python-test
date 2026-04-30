package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟持仓实体。
 */
@Data
@TableName("paper_positions")
public class PaperPositionDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账户 ID。 */
    private Long accountId;

    /** 股票代码。 */
    private String symbol;

    /** 持仓数量。 */
    private Integer positionQty;

    /** 可卖数量。 */
    private Integer availableQty;

    /** 持仓均价。 */
    private BigDecimal avgCost;

    /** 市值。 */
    private BigDecimal marketValue;

    /** 浮动盈亏。 */
    private BigDecimal floatingPnl;
}

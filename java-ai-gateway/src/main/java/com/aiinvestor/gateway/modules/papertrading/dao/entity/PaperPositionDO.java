package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟持仓实体 (DO)。
 * <p>
 * 对应数据库表 paper_positions，记录用户当前持有的股票仓位。
 * 每次成交后会更新持仓数量和均价，卖出时减少持仓。
 * 同一账户对同一只股票只保留一条持仓记录，通过平均成本法计算成本价。
 * </p>
 */
@Data
@TableName("paper_positions")
public class PaperPositionDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的模拟账户 ID */
    private Long accountId;

    /** 股票代码，如 "000001""600000" */
    private String symbol;

    /** 持仓数量（总持有股数） */
    private Integer positionQty;

    /** 可卖数量（已结算、可随时卖出的股数，通常 = 持仓数量） */
    private Integer availableQty;

    /** 持仓均价，加权平均后的买入成本价 */
    private BigDecimal avgCost;

    /** 持仓市值 = 持仓数量 * 当前最新价 */
    private BigDecimal marketValue;

    /** 浮动盈亏 = (当前最新价 - 持仓均价) * 持仓数量 */
    private BigDecimal floatingPnl;
}

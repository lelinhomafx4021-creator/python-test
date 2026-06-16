package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟成交记录实体 (DO)。
 * <p>
 * 对应数据库表 paper_trades，记录每一笔实际达成的交易。
 * 一笔委托可能分多次成交（部分成交），每次成交生成一条 trade 记录。
 * 成交后系统会同步更新：持仓数量/均价、账户现金/冻结资金、委托的已成交量。
 * </p>
 */
@Data
@TableName("paper_trades")
public class PaperTradeDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的委托订单 ID，对应 paper_orders.id */
    private Long orderId;

    /** 关联的模拟账户 ID */
    private Long accountId;

    /** 股票代码 */
    private String symbol;

    /** 买卖方向：BUY-买入, SELL-卖出 */
    private String side;

    /** 成交价格，撮合时的实际成交价 */
    private BigDecimal tradePrice;

    /** 本次成交数量，单位为股 */
    private Integer tradeQty;

    /** 本次成交金额 = 成交价 * 成交数量 */
    private BigDecimal tradeAmount;

    /** 成交时间 */
    private LocalDateTime tradeTime;
}

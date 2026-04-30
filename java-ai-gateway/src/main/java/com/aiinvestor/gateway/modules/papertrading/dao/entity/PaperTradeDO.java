package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟成交实体。
 */
@Data
@TableName("paper_trades")
public class PaperTradeDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 委托单 ID。 */
    private Long orderId;

    /** 账户 ID。 */
    private Long accountId;

    /** 股票代码。 */
    private String symbol;

    /** 买卖方向。 */
    private String side;

    /** 成交价。 */
    private BigDecimal tradePrice;

    /** 成交数量。 */
    private Integer tradeQty;

    /** 成交金额。 */
    private BigDecimal tradeAmount;

    /** 成交时间。 */
    private LocalDateTime tradeTime;
}

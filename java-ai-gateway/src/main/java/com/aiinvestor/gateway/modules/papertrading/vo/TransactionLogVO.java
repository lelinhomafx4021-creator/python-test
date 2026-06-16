package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易流水日志视图对象 (VO)。
 * <p>
 * 返回给前端展示的交易流水记录，涵盖下单、成交、撤单、充值、提现等各类事件。
 * 每条记录包含事件类型、股票代码、方向、价格、数量、金额及操作后余额等字段。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLogVO {

    /** 流水记录 ID */
    private Long id;

    /** 事件类型：ORDER_PLACED-下单/ORDER_FILLED-成交/ORDER_CANCELLED-撤单/DEPOSIT-充值/WITHDRAW-提现 */
    private String eventType;

    /** 股票代码（资金类事件为空） */
    private String symbol;

    /** 买卖方向：BUY-买入/SELL-卖出 */
    private String side;

    /** 数量（股） */
    private Integer quantity;

    /** 价格（元） */
    private BigDecimal price;

    /** 金额（元） */
    private BigDecimal amount;

    /** 操作后的账户现金余额（元） */
    private BigDecimal balanceAfter;

    /** 事件描述 */
    private String description;

    /** 事件发生时间 */
    private LocalDateTime createdAt;
}

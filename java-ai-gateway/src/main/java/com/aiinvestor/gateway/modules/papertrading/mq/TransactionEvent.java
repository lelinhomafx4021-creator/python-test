package com.aiinvestor.gateway.modules.papertrading.mq;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 交易事件，消息队列中传递的数据载体 (Record)。
 * <p>
 * 使用 Java Record 类型，天然不可变，线程安全，适合作为消息体在网络中传递。
 * 每条交易事件包含完整的交易流水信息：事件类型、标的、价格、
 * 数量、金额、操作后余额等，消费者收到后会异步入库到 transaction_logs
 * 表并创建用户通知。
 * </p>
 *
 * @param userId       用户 ID
 * @param eventType    事件类型：ORDER_PLACED-下单/ORDER_FILLED-成交/ORDER_CANCELLED-撤单/DEPOSIT-充值/WITHDRAW-提现
 * @param symbol       股票代码（交易类事件才有，资金类事件为空）
 * @param side         买卖方向：BUY-买入/SELL-卖出
 * @param quantity     数量（股）
 * @param price        成交价格（元）
 * @param amount       金额（元），交易时 = 价格 * 数量
 * @param balanceAfter 操作后账户现金余额
 * @param description  事件描述，用于前端展示
 * @param timestamp    事件发生时间戳
 * @author AI Investor Team
 */
public record TransactionEvent(
        Long userId,
        String eventType,
        String symbol,
        String side,
        Integer quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant timestamp
) {
}

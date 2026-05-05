package com.aiinvestor.gateway.modules.papertrading.mq;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ============================================================
 * 交易事件 - 消息队列中传递的数据载体
 * ============================================================
 *
 * 使用 Java Record 类型（不可变数据类），与 AiChatAuditEvent 模式一致。
 *
 * 包含完整的交易流水信息：事件类型、标的、价格、数量、金额、操作后余额等。
 * 消费者收到后会异步入库到 transaction_logs 表，同时创建用户通知。
 *
 * @param userId      用户 ID
 * @param eventType   事件类型：ORDER_PLACED/ORDER_FILLED/ORDER_CANCELLED/DEPOSIT/WITHDRAW
 * @param symbol      股票代码（交易类事件才有）
 * @param side        买卖方向：BUY/SELL
 * @param quantity    数量
 * @param price       成交价格
 * @param amount      金额
 * @param balanceAfter 操作后余额
 * @param description 描述
 * @param timestamp   事件发生时间
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

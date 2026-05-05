package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================
 * 交易流水日志实体 (DO)
 * ============================================================
 *
 * 对应数据库表 transaction_logs。
 *
 * 业务意义：
 *   通过 MQ 消费者异步写入，记录用户的每笔交易流水，
 *   包括下单、成交、撤单、充值、提现等事件。
 *   可用于：交易历史查询、用户通知、审计追溯、统计分析。
 *
 * @author AI Investor Team
 */
@Data
@Builder
@TableName("transaction_logs")
public class TransactionLogDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 事件类型：ORDER_PLACED/ORDER_FILLED/ORDER_CANCELLED/DEPOSIT/WITHDRAW */
    private String eventType;

    /** 股票代码（交易类事件才有） */
    private String symbol;

    /** 买卖方向：BUY/SELL */
    private String side;

    /** 数量 */
    private Integer quantity;

    /** 价格 */
    private BigDecimal price;

    /** 金额 */
    private BigDecimal amount;

    /** 操作后余额 */
    private BigDecimal balanceAfter;

    /** 描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

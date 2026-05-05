package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易流水日志视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLogVO {

    /** 记录 ID。 */
    private Long id;

    /** 事件类型。 */
    private String eventType;

    /** 股票代码。 */
    private String symbol;

    /** 买卖方向。 */
    private String side;

    /** 数量。 */
    private Integer quantity;

    /** 价格。 */
    private BigDecimal price;

    /** 金额。 */
    private BigDecimal amount;

    /** 操作后余额。 */
    private BigDecimal balanceAfter;

    /** 描述。 */
    private String description;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

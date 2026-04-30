package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟委托视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperOrderVO {

    /** 委托 ID。 */
    private Long id;

    /** 股票代码。 */
    private String symbol;

    /** 买卖方向。 */
    private String side;

    /** 委托类型。 */
    private String orderType;

    /** 委托价格。 */
    private BigDecimal orderPrice;

    /** 委托数量。 */
    private Integer orderQty;

    /** 成交数量。 */
    private Integer filledQty;

    /** 委托状态。 */
    private String orderStatus;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

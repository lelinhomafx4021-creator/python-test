package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟委托订单视图对象 (VO)。
 * <p>
 * 返回给前端展示的委托单信息，包含股票、方向、价格、数量、成交状态等关键字段。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperOrderVO {

    /** 委托订单 ID */
    private Long id;

    /** 股票代码 */
    private String symbol;

    /** 买卖方向：BUY-买入, SELL-卖出 */
    private String side;

    /** 委托类型：market-市价单, limit-限价单 */
    private String orderType;

    /** 委托价格（元），市价单显示为 null */
    private BigDecimal orderPrice;

    /** 委托数量（股） */
    private Integer orderQty;

    /** 已成交数量（股），部分成交时 < orderQty */
    private Integer filledQty;

    /** 委托状态：PENDING-待成交, PARTIAL-部分成交, FILLED-全部成交, CANCELLED-已撤销 */
    private String orderStatus;

    /** 委托创建时间 */
    private LocalDateTime createdAt;
}

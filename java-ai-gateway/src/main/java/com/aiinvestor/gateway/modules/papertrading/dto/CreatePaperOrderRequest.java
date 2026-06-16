package com.aiinvestor.gateway.modules.papertrading.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交模拟委托请求 DTO。
 * <p>
 * 用户发起买入/卖出委托时的入参对象，通过 @Valid 校验确保必填字段齐全。
 * 支持市价单（默认）和限价单两种委托类型，通过 clientRequestId 实现幂等。
 * </p>
 */
@Data
public class CreatePaperOrderRequest {

    /** 模拟账户 ID */
    @NotNull(message = "账户 ID 不能为空")
    private Long accountId;

    /** 股票代码，如 "000001""600000" */
    @NotBlank(message = "股票代码不能为空")
    private String symbol;

    /** 买卖方向：BUY-买入, SELL-卖出 */
    @NotBlank(message = "买卖方向不能为空")
    private String side;

    /** 委托类型：market-市价单（默认）, limit-限价单 */
    private String orderType = "market";

    /** 客户端幂等号，用于防止重复提交，建议用 UUID */
    private String clientRequestId;

    /** 委托价格，市价单可为空，限价单必填 */
    private BigDecimal orderPrice;

    /** 委托数量（股），必须大于 0 */
    @NotNull(message = "委托数量不能为空")
    @Min(value = 1, message = "委托数量必须大于 0")
    private Integer orderQty;
}

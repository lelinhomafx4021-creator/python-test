package com.aiinvestor.gateway.modules.papertrading.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交模拟委托请求。
 */
@Data
public class CreatePaperOrderRequest {

    /** 账户 ID。 */
    @NotNull(message = "账户 ID 不能为空")
    private Long accountId;

    /** 股票代码。 */
    @NotBlank(message = "股票代码不能为空")
    private String symbol;

    /** 买卖方向：BUY/SELL。 */
    @NotBlank(message = "买卖方向不能为空")
    private String side;

    /** 委托类型。 */
    private String orderType = "market";

    /** 客户端幂等号。 */
    private String clientRequestId;

    /** 委托价格，可为空。 */
    private BigDecimal orderPrice;

    /** 委托数量。 */
    @NotNull(message = "委托数量不能为空")
    @Min(value = 1, message = "委托数量必须大于 0")
    private Integer orderQty;
}

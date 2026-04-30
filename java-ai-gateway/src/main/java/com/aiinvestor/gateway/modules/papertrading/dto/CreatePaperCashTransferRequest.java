package com.aiinvestor.gateway.modules.papertrading.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建模拟充值请求。
 */
@Data
public class CreatePaperCashTransferRequest {

    /** 模拟账户 ID。 */
    @NotNull(message = "账户不能为空")
    private Long accountId;

    /** 充值金额。 */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    private BigDecimal amount;

    /** 备注。 */
    private String remark;
}

package com.aiinvestor.gateway.modules.papertrading.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建模拟资金划转请求 DTO。
 * <p>
 * 用户发起充值或提现操作时的入参对象，通过 @Valid 校验确保数据合法性。
 * </p>
 */
@Data
public class CreatePaperCashTransferRequest {

    /** 目标模拟账户 ID */
    @NotNull(message = "账户不能为空")
    private Long accountId;

    /** 划转金额（元），用于充值或提现，必须大于 0 */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    private BigDecimal amount;

    /** 备注信息，可选 */
    private String remark;
}

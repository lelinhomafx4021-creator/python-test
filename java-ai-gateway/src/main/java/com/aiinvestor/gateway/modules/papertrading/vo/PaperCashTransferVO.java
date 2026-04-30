package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟充值转账记录视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCashTransferVO {

    /** 记录 ID。 */
    private Long id;

    /** 资金方向。 */
    private String direction;

    /** 渠道编码。 */
    private String channelCode;

    /** 渠道名称。 */
    private String channelName;

    /** 商户订单号。 */
    private String outTradeNo;

    /** 渠道流水号。 */
    private String channelTradeNo;

    /** 金额。 */
    private BigDecimal amount;

    /** 状态。 */
    private String status;

    /** 备注。 */
    private String remark;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 到账时间。 */
    private LocalDateTime paidAt;
}

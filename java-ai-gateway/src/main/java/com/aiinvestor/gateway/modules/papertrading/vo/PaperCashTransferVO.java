package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟资金划转记录视图对象 (VO)。
 * <p>
 * 返回给前端展示的充值/提现流水记录，包含资金方向、金额、渠道和时间信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCashTransferVO {

    /** 划转记录 ID */
    private Long id;

    /** 资金方向：DEPOSIT-入金（充值）, WITHDRAW-出金（提现） */
    private String direction;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称，如"模拟充值""模拟提现" */
    private String channelName;

    /** 商户订单号，业务侧唯一流水号 */
    private String outTradeNo;

    /** 渠道流水号 */
    private String channelTradeNo;

    /** 划转金额（元） */
    private BigDecimal amount;

    /** 划转状态：PENDING-待处理, SUCCESS-已到账, FAILED-失败 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 到账时间，资金实际确认的时间 */
    private LocalDateTime paidAt;
}

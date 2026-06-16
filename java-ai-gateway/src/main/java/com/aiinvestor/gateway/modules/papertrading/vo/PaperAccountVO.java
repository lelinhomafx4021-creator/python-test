package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟交易账户视图对象 (VO)。
 * <p>
 * 返回给前端的账户摘要信息，脱敏后只包含用户关注的资金和盈亏数据。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperAccountVO {

    /** 模拟账户 ID */
    private Long id;

    /** 模拟账户号码 */
    private String accountNo;

    /** 可用现金余额（元），可随时用于下单 */
    private BigDecimal cashBalance;

    /** 冻结现金（元），委托买入时占用 */
    private BigDecimal frozenCash;

    /** 账户总资产（元）= 现金余额 + 持仓市值 */
    private BigDecimal totalAsset;

    /** 累计盈亏（元），账户创建以来的总盈亏 */
    private BigDecimal totalPnl;

    /** 账户状态：NORMAL-正常, FROZEN-冻结, CLOSED-已注销 */
    private String status;
}

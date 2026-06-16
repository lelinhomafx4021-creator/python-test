package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟资金划转记录实体 (DO)。
 * <p>
 * 对应数据库表 paper_cash_transfers，记录用户在模拟账户中的入金和出金操作。
 * 模拟交易场景中用户可随时"充值"增加本金，或"提现"减少本金，以便灵活测试不同资金量下的交易策略。
 * 每条记录对应一次资金划转，包含方向、金额、渠道信息及状态。
 * </p>
 */
@Data
@TableName("paper_cash_transfers")
public class PaperCashTransferDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的模拟账户 ID */
    private Long accountId;

    /** 用户 ID */
    private Long userId;

    /** 资金方向：DEPOSIT-入金（充值）, WITHDRAW-出金（提现） */
    private String direction;

    /** 渠道编码，标识资金来源或去向渠道 */
    private String channelCode;

    /** 渠道名称（如"模拟充值""模拟提现"） */
    private String channelName;

    /** 商户订单号，业务侧生成的唯一流水号，用于幂等防重 */
    private String outTradeNo;

    /** 渠道返回的流水号，对应实际资金渠道的交易号 */
    private String channelTradeNo;

    /** 划转金额，单位元 */
    private BigDecimal amount;

    /** 划转状态：PENDING-待处理, SUCCESS-已到账, FAILED-失败 */
    private String status;

    /** 备注信息 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 到账时间，资金实际确认的时间 */
    private LocalDateTime paidAt;
}

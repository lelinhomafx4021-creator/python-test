package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟资金充值转账记录实体。
 */
@Data
@TableName("paper_cash_transfers")
public class PaperCashTransferDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模拟账户 ID。 */
    private Long accountId;

    /** 用户 ID。 */
    private Long userId;

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

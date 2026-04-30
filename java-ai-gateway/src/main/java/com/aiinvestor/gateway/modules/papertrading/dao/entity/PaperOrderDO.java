package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟委托单实体。
 */
@Data
@TableName("paper_orders")
public class PaperOrderDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账户 ID。 */
    private Long accountId;

    /** 股票代码。 */
    private String symbol;

    /** 买卖方向。 */
    private String side;

    /** 委托类型。 */
    private String orderType;

    /** 委托价格。 */
    private BigDecimal orderPrice;

    /** 委托数量。 */
    private Integer orderQty;

    /** 成交数量。 */
    private Integer filledQty;

    /** 委托状态。 */
    private String orderStatus;

    /** 客户端幂等号。 */
    private String clientRequestId;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

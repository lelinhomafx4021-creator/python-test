package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟委托订单实体 (DO)。
 * <p>
 * 对应数据库表 paper_orders，记录用户提交的每一笔买入/卖出委托。
 * 委托是模拟交易的核心单据：用户指定股票、方向、价格、数量后生成委托，
 * 系统根据市场行情撮合成交，成交信息记录在 paper_trades 表中。
 * 支持市价单和限价单两种委托类型，通过 clientRequestId 实现幂等提交。
 * </p>
 */
@Data
@TableName("paper_orders")
public class PaperOrderDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的模拟账户 ID */
    private Long accountId;

    /** 股票代码，如 "000001""600000" */
    private String symbol;

    /** 买卖方向：BUY-买入, SELL-卖出 */
    private String side;

    /** 委托类型：market-市价单, limit-限价单 */
    private String orderType;

    /** 委托价格（限价单必填，市价单可空） */
    private BigDecimal orderPrice;

    /** 委托数量，单位为股 */
    private Integer orderQty;

    /** 已成交数量，随成交逐步累加 */
    private Integer filledQty;

    /** 委托状态：PENDING-待成交, PARTIAL-部分成交, FILLED-全部成交, CANCELLED-已撤销 */
    private String orderStatus;

    /** 客户端幂等号，用于防止重复提交 */
    private String clientRequestId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

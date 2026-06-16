package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟交易账户实体 (DO)。
 * <p>
 * 对应数据库表 paper_accounts，记录用户的模拟交易账户信息。
 * 每个用户可以拥有一个或多个模拟账户，用于学习股票交易操作。
 * 账户包含现金余额、冻结资金、总资产和累计盈亏等核心资金字段。
 * </p>
 */
@Data
@TableName("paper_accounts")
public class PaperAccountDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，关联用户表 */
    private Long userId;

    /** 模拟账户号码，业务唯一标识 */
    private String accountNo;

    /** 可用现金余额，可随时用于下单的现金 */
    private BigDecimal cashBalance;

    /** 冻结现金，已占用但未成交的现金（如委托买入时冻结） */
    private BigDecimal frozenCash;

    /** 账户总资产 = 现金余额 + 持仓市值 */
    private BigDecimal totalAsset;

    /** 累计盈亏，自账户创建以来的总盈亏金额 */
    private BigDecimal totalPnl;

    /** 账户状态：NORMAL-正常, FROZEN-冻结, CLOSED-已注销 */
    private String status;
}

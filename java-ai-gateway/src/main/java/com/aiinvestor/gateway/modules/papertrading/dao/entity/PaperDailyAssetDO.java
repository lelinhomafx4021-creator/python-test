package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 模拟账户每日资产快照实体 (DO)。
 * <p>
 * 对应数据库表 paper_daily_assets，记录每个交易日结束时的账户资产汇总数据。
 * 每日定时生成一份快照，用于绘制资金曲线图、计算日收益率等可视化分析。
 * 每个账户每个交易日仅保留一条记录。
 * </p>
 */
@Data
@TableName("paper_daily_assets")
public class PaperDailyAssetDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的模拟账户 ID */
    private Long accountId;

    /** 交易日日期 */
    private LocalDate tradeDate;

    /** 当日结算时的现金余额 */
    private BigDecimal cashBalance;

    /** 当日结算时的持仓总市值 */
    private BigDecimal marketValue;

    /** 当日结算时的总资产 = 现金余额 + 持仓市值 */
    private BigDecimal totalAsset;

    /** 当日盈亏金额（与上一交易日对比的变动额） */
    private BigDecimal dailyPnl;
}

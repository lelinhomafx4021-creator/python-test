package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 模拟日资产快照实体。
 */
@Data
@TableName("paper_daily_assets")
public class PaperDailyAssetDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账户 ID。 */
    private Long accountId;

    /** 日期。 */
    private LocalDate tradeDate;

    /** 现金。 */
    private BigDecimal cashBalance;

    /** 市值。 */
    private BigDecimal marketValue;

    /** 总资产。 */
    private BigDecimal totalAsset;

    /** 当日盈亏。 */
    private BigDecimal dailyPnl;
}

package com.aiinvestor.gateway.modules.papertrading.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟账户实体。
 */
@Data
@TableName("paper_accounts")
public class PaperAccountDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 账户号。 */
    private String accountNo;

    /** 可用现金。 */
    private BigDecimal cashBalance;

    /** 冻结现金。 */
    private BigDecimal frozenCash;

    /** 总资产。 */
    private BigDecimal totalAsset;

    /** 累计盈亏。 */
    private BigDecimal totalPnl;

    /** 状态。 */
    private String status;
}

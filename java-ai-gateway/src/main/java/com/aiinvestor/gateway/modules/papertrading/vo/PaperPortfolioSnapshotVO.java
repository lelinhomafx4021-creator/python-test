package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟投资组合快照视图对象 (VO)。
 * <p>
 * 整合账户总览和实时持仓两类数据，合并为一个接口返回，方便前端定时刷新整个投资组合面板。
 * 包含账户资金摘要、全部持仓明细列表以及本次快照的刷新时间戳。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperPortfolioSnapshotVO {

    /** 账户资金摘要信息 */
    private PaperAccountVO account;

    /** 当前全部持仓列表，按股票聚合 */
    private List<PaperPositionVO> positions;

    /** 本次快照刷新的服务器时间 */
    private LocalDateTime refreshedAt;
}

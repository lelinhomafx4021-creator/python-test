package com.aiinvestor.gateway.modules.papertrading.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持仓快照视图。
 * 把账户总览、实时持仓和刷新时间收口成一个接口返回，方便前端定时刷新。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperPortfolioSnapshotVO {

    /** 账户信息。 */
    private PaperAccountVO account;

    /** 持仓列表。 */
    private List<PaperPositionVO> positions;

    /** 本次快照刷新时间。 */
    private LocalDateTime refreshedAt;
}

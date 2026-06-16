package com.aiinvestor.gateway.modules.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端首页总览数据视图。
 * <p>
 * 汇总整个系统的核心业务指标，包括用户数、会员数、AI 会话数、
 * 工单数、自选分组数、模拟账户数、交易流水数等。
 * 通过 CompletableFuture 并行查询 9 个维度，避免串行等待。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardVO {

    /** 注册用户总数。 */
    private Long totalUsers;

    /** 当前 VIP 有效会员数。 */
    private Long totalVipUsers;

    /** 管理员用户数。 */
    private Long totalAdminUsers;

    /** AI 投研会话历史总数。 */
    private Long totalAiSessions;

    /** 人工兜底工单总数。 */
    private Long totalHandoffTickets;

    /** 未关闭（open + processing）的工单数。 */
    private Long openHandoffTickets;

    /** 自选股分组总数。 */
    private Long totalWatchlists;

    /** 模拟交易账户总数。 */
    private Long totalPaperAccounts;

    /** 交易流水记录总数。 */
    private Long totalTransactionLogs;
}

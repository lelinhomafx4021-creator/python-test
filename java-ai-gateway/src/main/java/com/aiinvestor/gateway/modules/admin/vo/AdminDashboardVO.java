package com.aiinvestor.gateway.modules.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端首页总览数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardVO {

    /** 用户总数。 */
    private Long totalUsers;

    /** VIP 会员数。 */
    private Long totalVipUsers;

    /** 管理员数。 */
    private Long totalAdminUsers;

    /** AI 会话总数。 */
    private Long totalAiSessions;

    /** 人工工单总数。 */
    private Long totalHandoffTickets;

    /** 未关闭工单数。 */
    private Long openHandoffTickets;

    /** 自选分组总数。 */
    private Long totalWatchlists;

    /** 模拟账户总数。 */
    private Long totalPaperAccounts;

    /** 交易流水总数。 */
    private Long totalTransactionLogs;
}

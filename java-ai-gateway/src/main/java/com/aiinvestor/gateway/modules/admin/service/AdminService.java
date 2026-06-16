package com.aiinvestor.gateway.modules.admin.service;

import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.ai.dao.entity.AiHandoffTicketDO;
import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.ai.dao.mapper.AiHandoffTicketMapper;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserMapper;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateMembershipRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateTicketStatusRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateUserRoleRequest;
import com.aiinvestor.gateway.modules.admin.vo.AdminDashboardVO;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminUserPortfolioVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminUserVO;
import com.aiinvestor.gateway.modules.ai.dao.entity.AiSessionDO;
import com.aiinvestor.gateway.modules.ai.dao.mapper.AiSessionMapper;
import com.aiinvestor.gateway.modules.membership.dao.entity.UserFeatureQuotaDO;
import com.aiinvestor.gateway.modules.membership.dao.entity.UserMembershipDO;
import com.aiinvestor.gateway.modules.membership.dao.mapper.UserFeatureQuotaMapper;
import com.aiinvestor.gateway.modules.membership.dao.mapper.UserMembershipMapper;
import com.aiinvestor.gateway.modules.membership.service.MembershipService;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperAccountDO;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperAccountMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.TransactionLogDO;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.TransactionLogMapper;
import com.aiinvestor.gateway.modules.papertrading.service.PaperTradingService;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPortfolioSnapshotVO;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.aiinvestor.gateway.modules.watchlist.dao.entity.WatchlistDO;
import com.aiinvestor.gateway.modules.watchlist.dao.mapper.WatchlistMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理后台业务服务。
 * <p>
 * 封装管理员专属操作：数据总览（并行查询）、用户管理（列表/角色/会员）、
 * 工单处理、用户持仓查看。所有写操作调用前会校验管理员身份。
 * <p>
 * 总览查询使用 CompletableFuture 并行执行 9 个独立统计查询，
 * 将总耗时从串行的 sum(t1..t9) 降为 max(t1..t9)。
 */
@Service
public class AdminService {

    private final UserMapper userMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final UserFeatureQuotaMapper userFeatureQuotaMapper;
    private final AiSessionMapper aiSessionMapper;
    private final AiHandoffTicketMapper aiHandoffTicketMapper;
    private final WatchlistMapper watchlistMapper;
    private final PaperAccountMapper paperAccountMapper;
    private final TransactionLogMapper transactionLogMapper;
    private final PaperTradingService paperTradingService;
    private final MembershipService membershipService;

    /**
     * 构造函数，注入全部依赖的 Mapper 和 Service。
     */
    public AdminService(UserMapper userMapper,
                        UserMembershipMapper userMembershipMapper,
                        UserFeatureQuotaMapper userFeatureQuotaMapper,
                        AiSessionMapper aiSessionMapper,
                        AiHandoffTicketMapper aiHandoffTicketMapper,
                        WatchlistMapper watchlistMapper,
                        PaperAccountMapper paperAccountMapper,
                        TransactionLogMapper transactionLogMapper,
                        PaperTradingService paperTradingService,
                        MembershipService membershipService) {
        this.userMapper = userMapper;
        this.userMembershipMapper = userMembershipMapper;
        this.userFeatureQuotaMapper = userFeatureQuotaMapper;
        this.aiSessionMapper = aiSessionMapper;
        this.aiHandoffTicketMapper = aiHandoffTicketMapper;
        this.watchlistMapper = watchlistMapper;
        this.paperAccountMapper = paperAccountMapper;
        this.transactionLogMapper = transactionLogMapper;
        this.paperTradingService = paperTradingService;
        this.membershipService = membershipService;
    }

    /**
     * 查询管理端首页总览。
     * <p>
     * 使用 9 个 CompletableFuture 并行统计：用户数、VIP 数、管理员数、
     * AI 会话数、工单总数、未关闭工单数、自选分组数、模拟账户数、交易流水数。
     *
     * @return 仪表盘总览视图
     */
    public AdminDashboardVO getDashboard() {
        assertAdmin();

        // 9 个独立查询并行执行，总耗时从 sum 降为 max
        CompletableFuture<Long> fTotalUsers = CompletableFuture.supplyAsync(() -> userMapper.selectCount(null));
        CompletableFuture<Long> fTotalVip = CompletableFuture.supplyAsync(() -> userMembershipMapper.selectCount(
                new LambdaQueryWrapper<UserMembershipDO>()
                        .eq(UserMembershipDO::getPlanCode, "vip")
                        .eq(UserMembershipDO::getStatus, "active")));
        CompletableFuture<Long> fTotalAdmin = CompletableFuture.supplyAsync(() -> userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getRole, "admin")));
        CompletableFuture<Long> fTotalSessions = CompletableFuture.supplyAsync(() -> aiSessionMapper.selectCount(new LambdaQueryWrapper<AiSessionDO>()));
        CompletableFuture<Long> fTotalTickets = CompletableFuture.supplyAsync(() -> aiHandoffTicketMapper.selectCount(new LambdaQueryWrapper<AiHandoffTicketDO>()));
        CompletableFuture<Long> fOpenTickets = CompletableFuture.supplyAsync(() -> aiHandoffTicketMapper.selectCount(
                new LambdaQueryWrapper<AiHandoffTicketDO>()
                        .and(w -> w.isNull(AiHandoffTicketDO::getStatus).or().ne(AiHandoffTicketDO::getStatus, "closed"))));
        CompletableFuture<Long> fTotalWatchlists = CompletableFuture.supplyAsync(() -> watchlistMapper.selectCount(new LambdaQueryWrapper<WatchlistDO>()));
        CompletableFuture<Long> fTotalPaper = CompletableFuture.supplyAsync(() -> paperAccountMapper.selectCount(new LambdaQueryWrapper<PaperAccountDO>()));
        CompletableFuture<Long> fTotalTxLogs = CompletableFuture.supplyAsync(() -> transactionLogMapper.selectCount(new LambdaQueryWrapper<TransactionLogDO>()));

        CompletableFuture.allOf(fTotalUsers, fTotalVip, fTotalAdmin, fTotalSessions,
                fTotalTickets, fOpenTickets, fTotalWatchlists, fTotalPaper, fTotalTxLogs).join();

        return new AdminDashboardVO(
                fTotalUsers.join(), fTotalVip.join(), fTotalAdmin.join(),
                fTotalSessions.join(), fTotalTickets.join(), fOpenTickets.join(),
                fTotalWatchlists.join(), fTotalPaper.join(), fTotalTxLogs.join()
        );
    }

    /**
     * 查询用户列表（支持模糊搜索）。
     * <p>
     * 当 keyword 非空时，按用户名/昵称/手机号模糊匹配，最多返回 200 条。
     * 同时对 userId 批量查询会员方案和 AI 配额，减少 N+1 问题。
     *
     * @param keyword 搜索关键词（匹配用户名/昵称/手机号），null 时返回全量
     * @return 用户列表（含会员信息、AI 额度、自选分组数）
     */
    public List<AdminUserVO> listUsers(String keyword) {
        assertAdmin();

        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
                .orderByDesc(UserDO::getId)
                .last("limit 200");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query -> query.like(UserDO::getUsername, keyword)
                    .or()
                    .like(UserDO::getNickname, keyword)
                    .or()
                    .like(UserDO::getPhone, keyword));
        }

        List<UserDO> users = userMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = users.stream().map(UserDO::getId).toList();

        Map<Long, UserMembershipDO> membershipMap = userMembershipMapper.selectList(
                        new LambdaQueryWrapper<UserMembershipDO>()
                                .in(UserMembershipDO::getUserId, userIds)
                                .eq(UserMembershipDO::getStatus, "active")
                                .orderByDesc(UserMembershipDO::getId)
                ).stream()
                .collect(Collectors.toMap(
                        UserMembershipDO::getUserId,
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<Long, UserFeatureQuotaDO> aiQuotaMap = userFeatureQuotaMapper.selectList(
                        new LambdaQueryWrapper<UserFeatureQuotaDO>()
                                .in(UserFeatureQuotaDO::getUserId, userIds)
                                .eq(UserFeatureQuotaDO::getFeatureCode, "ai_chat_daily")
                ).stream()
                .collect(Collectors.toMap(
                        UserFeatureQuotaDO::getUserId,
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<Long, Long> watchlistCountMap = watchlistMapper.selectList(
                        new LambdaQueryWrapper<WatchlistDO>().in(WatchlistDO::getUserId, userIds)
                ).stream()
                .collect(Collectors.groupingBy(WatchlistDO::getUserId, Collectors.counting()));

        List<AdminUserVO> result = new ArrayList<>();
        for (UserDO user : users) {
            UserMembershipDO membership = membershipMap.get(user.getId());
            UserFeatureQuotaDO quota = aiQuotaMap.get(user.getId());

            AdminUserVO row = new AdminUserVO();
            row.setId(user.getId());
            row.setUsername(user.getUsername());
            row.setNickname(user.getNickname());
            row.setPhone(user.getPhone());
            row.setRole(user.getRole());
            row.setStatus(user.getStatus());
            row.setAvatarUrl(user.getAvatarUrl());
            row.setPlanCode(membership != null ? membership.getPlanCode() : null);
            row.setMembershipStatus(membership != null ? membership.getStatus() : null);
            row.setAiChatLimit(quota != null ? quota.getLimitCount() : 0);
            row.setAiChatUsed(quota != null ? quota.getUsedCount() : 0);
            row.setWatchlistCount(watchlistCountMap.getOrDefault(user.getId(), 0L).intValue());
            row.setLastLoginAt(user.getLastLoginAt());
            row.setCreatedAt(user.getCreatedAt());
            result.add(row);
        }
        return result;
    }

    /**
     * 查询人工工单列表（按状态筛选）。
     * <p>
     * 最多返回 200 条，按创建时间倒序。同时批量填充工单关联的用户信息。
     *
     * @param status 工单状态筛选（null 时返回全部）
     * @return 工单列表（含关联用户名/昵称）
     */
    public List<HandoffTicketVO> listHandoffTickets(String status) {
        assertAdmin();

        LambdaQueryWrapper<AiHandoffTicketDO> wrapper = new LambdaQueryWrapper<AiHandoffTicketDO>()
                .orderByDesc(AiHandoffTicketDO::getCreatedAt)
                .last("limit 200");
        if (status != null && !status.isBlank()) {
            wrapper.eq(AiHandoffTicketDO::getStatus, status);
        }

        List<AiHandoffTicketDO> tickets = aiHandoffTicketMapper.selectList(wrapper);
        if (tickets.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = tickets.stream()
                .map(AiHandoffTicketDO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, UserDO> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity()));

        List<HandoffTicketVO> result = new ArrayList<>();
        for (AiHandoffTicketDO ticket : tickets) {
            Long userId = ticket.getUserId();
            UserDO user = userId == null ? null : userMap.get(userId);

            HandoffTicketVO row = new HandoffTicketVO();
            row.setTraceId(ticket.getTraceId());
            row.setUserId(ticket.getUserId());
            row.setUsername(user != null ? user.getUsername() : null);
            row.setNickname(user != null ? user.getNickname() : null);
            row.setSessionId(ticket.getSessionId());
            row.setQuery(ticket.getQuery());
            row.setHandoffReason(ticket.getHandoffReason());
            row.setHandoffSummary(ticket.getHandoffSummary());
            row.setStatus(ticket.getStatus());
            row.setProcessNote(ticket.getProcessNote());
            row.setResponseMessage(ticket.getResponseMessage());
            row.setHandledBy(ticket.getHandledBy());
            row.setHandledAt(ticket.getHandledAt());
            row.setCreatedAt(ticket.getCreatedAt());
            row.setUpdatedAt(ticket.getUpdatedAt());
            result.add(row);
        }
        return result;
    }

    /**
     * 查询指定用户的持仓与委托。
     * <p>
     * 先校验目标用户存在，再获取模拟账户快照和委托记录。
     *
     * @param userId       目标用户 ID
     * @param refreshQuote 是否强制刷新实时行情（true 时跳过缓存）
     * @return 用户持仓组合视图，包含账户、持仓和最近委托
     */
    public AdminUserPortfolioVO getUserPortfolio(Long userId, boolean refreshQuote) {
        assertAdmin();

        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("目标用户不存在");
        }

        PaperPortfolioSnapshotVO snapshot = paperTradingService.getPortfolioSnapshotForAdmin(userId, refreshQuote);
        return new AdminUserPortfolioVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                snapshot.getAccount(),
                snapshot.getPositions(),
                paperTradingService.listOrdersForAdmin(userId)
        );
    }

    /**
     * 修改用户角色。
     * <p>
     * 先校验目标用户存在，再校验角色值合法（guest/normal/vip/admin 之一），
     * 然后更新数据库。
     *
     * @param userId  目标用户 ID
     * @param request 新角色信息
     */
    public void updateUserRole(Long userId, AdminUpdateUserRoleRequest request) {
        assertAdmin();

        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("目标用户不存在");
        }

        String role = normalizeRole(request.getRole());
        user.setRole(role);
        userMapper.updateById(user);
    }

    /**
     * 修改用户会员方案。
     * <p>
     * 委托 MembershipService 为新方案创建功能配额并清理旧方案残留。
     *
     * @param userId  目标用户 ID
     * @param request 新会员方案信息
     */
    public void updateMembership(Long userId, AdminUpdateMembershipRequest request) {
        assertAdmin();

        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("目标用户不存在");
        }

        membershipService.assignMembershipPlanByAdmin(userId, user.getRole(), request.getPlanCode().trim());
    }

    /**
     * 修改工单状态。
     * <p>
     * 通过 traceId 定位工单，校验状态值合法（open/processing/closed 之一），
     * 记录处理人、处理时间、处理备注和用户回复内容。
     *
     * @param traceId 工单追踪 ID（全局唯一）
     * @param request 新状态及处理信息
     * @return 更新后的工单视图
     */
    public HandoffTicketVO updateTicketStatus(String traceId, AdminUpdateTicketStatusRequest request) {
        assertAdmin();

        AiHandoffTicketDO ticket = aiHandoffTicketMapper.selectOne(
                new LambdaQueryWrapper<AiHandoffTicketDO>()
                        .eq(AiHandoffTicketDO::getTraceId, traceId)
                        .last("limit 1")
        );
        if (ticket == null) {
            throw new BusinessException("目标工单不存在");
        }

        ticket.setStatus(normalizeTicketStatus(request.getStatus()));
        ticket.setProcessNote(trimToNull(request.getProcessNote()));
        ticket.setResponseMessage(trimToNull(request.getResponseMessage()));
        UserDO currentUser = UserContext.get();
        ticket.setHandledBy(currentUser != null ? currentUser.getUsername() : null);
        ticket.setHandledAt(LocalDateTime.now());
        aiHandoffTicketMapper.updateById(ticket);
        return toAdminTicket(ticket);
    }

    /**
     * 校验当前登录人是否为管理员。
     * <p>
     * 从 UserContext 获取当前用户，检查角色是否为 "admin"。
     * 非管理员调用时抛出 BusinessException。
     */
    private void assertAdmin() {
        UserDO currentUser = UserContext.get();
        if (currentUser == null || !"admin".equalsIgnoreCase(currentUser.getRole())) {
            throw new BusinessException("仅管理员可访问管理端");
        }
    }

    /**
     * 规范角色值。
     */
    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!Set.of("guest", "normal", "vip", "admin").contains(normalized)) {
            throw new BusinessException("不支持的角色：" + role);
        }
        return normalized;
    }

    /**
     * 规范工单状态。
     */
    private String normalizeTicketStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (!Set.of("open", "processing", "closed").contains(normalized)) {
            throw new BusinessException("不支持的工单状态：" + status);
        }
        return normalized;
    }
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private HandoffTicketVO toAdminTicket(AiHandoffTicketDO ticket) {
        Long userId = ticket.getUserId();
        UserDO user = userId == null ? null : userMapper.selectById(userId);

        HandoffTicketVO row = new HandoffTicketVO();
        row.setTraceId(ticket.getTraceId());
        row.setUserId(ticket.getUserId());
        row.setUsername(user != null ? user.getUsername() : null);
        row.setNickname(user != null ? user.getNickname() : null);
        row.setSessionId(ticket.getSessionId());
        row.setQuery(ticket.getQuery());
        row.setHandoffReason(ticket.getHandoffReason());
        row.setHandoffSummary(ticket.getHandoffSummary());
        row.setStatus(ticket.getStatus());
        row.setProcessNote(ticket.getProcessNote());
        row.setResponseMessage(ticket.getResponseMessage());
        row.setHandledBy(ticket.getHandledBy());
        row.setHandledAt(ticket.getHandledAt());
        row.setCreatedAt(ticket.getCreatedAt());
        row.setUpdatedAt(ticket.getUpdatedAt());
        return row;
    }
}

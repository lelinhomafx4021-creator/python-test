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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端服务。
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
     */
    public AdminDashboardVO getDashboard() {
        assertAdmin();

        long totalUsers = userMapper.selectCount(null);
        long totalVipUsers = userMembershipMapper.selectCount(
                new LambdaQueryWrapper<UserMembershipDO>()
                        .eq(UserMembershipDO::getPlanCode, "vip")
                        .eq(UserMembershipDO::getStatus, "active")
        );
        long totalAdminUsers = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getRole, "admin")
        );
        long totalAiSessions = aiSessionMapper.selectCount(new LambdaQueryWrapper<AiSessionDO>());
        long totalHandoffTickets = aiHandoffTicketMapper.selectCount(new LambdaQueryWrapper<AiHandoffTicketDO>());
        long openHandoffTickets = aiHandoffTicketMapper.selectCount(
                new LambdaQueryWrapper<AiHandoffTicketDO>()
                        .and(wrapper -> wrapper.isNull(AiHandoffTicketDO::getStatus)
                                .or()
                                .ne(AiHandoffTicketDO::getStatus, "closed"))
        );
        long totalWatchlists = watchlistMapper.selectCount(new LambdaQueryWrapper<WatchlistDO>());
        long totalPaperAccounts = paperAccountMapper.selectCount(new LambdaQueryWrapper<PaperAccountDO>());
        long totalTransactionLogs = transactionLogMapper.selectCount(new LambdaQueryWrapper<TransactionLogDO>());

        return new AdminDashboardVO(
                totalUsers,
                totalVipUsers,
                totalAdminUsers,
                totalAiSessions,
                totalHandoffTickets,
                openHandoffTickets,
                totalWatchlists,
                totalPaperAccounts,
                totalTransactionLogs
        );
    }

    /**
     * 查询用户列表。
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
     * 查询人工工单列表。
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

package com.aiinvestor.gateway.modules.membership.service;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserMapper;
import com.aiinvestor.gateway.modules.membership.dao.entity.MembershipPlanDO;
import com.aiinvestor.gateway.modules.membership.dao.entity.UserFeatureQuotaDO;
import com.aiinvestor.gateway.modules.membership.dao.entity.UserMembershipDO;
import com.aiinvestor.gateway.modules.membership.dao.mapper.MembershipPlanMapper;
import com.aiinvestor.gateway.modules.membership.dao.mapper.UserFeatureQuotaMapper;
import com.aiinvestor.gateway.modules.membership.dao.mapper.UserMembershipMapper;
import com.aiinvestor.gateway.modules.membership.vo.FeatureQuotaVO;
import com.aiinvestor.gateway.modules.membership.vo.MembershipInfoVO;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会员域服务。
 * 负责会员方案绑定、默认配额初始化以及配额同步。
 */
@Service
public class MembershipService {

    private final MembershipPlanMapper membershipPlanMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final UserFeatureQuotaMapper userFeatureQuotaMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public MembershipService(MembershipPlanMapper membershipPlanMapper,
                             UserMembershipMapper userMembershipMapper,
                             UserFeatureQuotaMapper userFeatureQuotaMapper,
                             UserMapper userMapper,
                             ObjectMapper objectMapper) {
        this.membershipPlanMapper = membershipPlanMapper;
        this.userMembershipMapper = userMembershipMapper;
        this.userFeatureQuotaMapper = userFeatureQuotaMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取当前会员信息，不存在时自动补默认会员。
     */
    @Transactional
    public MembershipInfoVO getCurrentMembership(Long userId, String role) {
        UserMembershipDO membership = ensureMembership(userId, role);
        MembershipPlanDO plan = getPlanByCode(membership.getPlanCode());
        ensureQuotaRecords(userId, plan);
        return new MembershipInfoVO(
                plan.getPlanCode(),
                plan.getPlanName(),
                plan.getPrice(),
                plan.getBillingCycle(),
                membership.getStatus(),
                membership.getStartAt(),
                membership.getEndAt()
        );
    }

    /**
     * 获取当前用户全部配额。
     */
    @Transactional
    public List<FeatureQuotaVO> listQuotas(Long userId, String role) {
        MembershipPlanDO plan = getPlanByCode(ensureMembership(userId, role).getPlanCode());
        List<UserFeatureQuotaDO> quotas = ensureQuotaRecords(userId, plan);
        refreshExpiredQuotas(quotas);
        return quotas.stream()
                .map(item -> new FeatureQuotaVO(
                        item.getFeatureCode(),
                        item.getPeriodType(),
                        item.getLimitCount(),
                        item.getUsedCount(),
                        item.getResetAt()
                ))
                .toList();
    }

    /**
     * 获取单个配额上限。
     */
    @Transactional
    public int getQuotaLimit(Long userId, String role, String featureCode) {
        MembershipPlanDO plan = getPlanByCode(ensureMembership(userId, role).getPlanCode());
        List<UserFeatureQuotaDO> quotas = ensureQuotaRecords(userId, plan);
        refreshExpiredQuotas(quotas);
        return quotas.stream()
                .filter(item -> featureCode.equals(item.getFeatureCode()))
                .findFirst()
                .map(UserFeatureQuotaDO::getLimitCount)
                .orElse(0);
    }

    /**
     * 同步永久型配额已用数量。
     */
    @Transactional
    public void syncPermanentQuota(Long userId, String role, String featureCode, int usedCount) {
        MembershipPlanDO plan = getPlanByCode(ensureMembership(userId, role).getPlanCode());
        List<UserFeatureQuotaDO> quotas = ensureQuotaRecords(userId, plan);
        quotas.stream()
                .filter(item -> featureCode.equals(item.getFeatureCode()))
                .findFirst()
                .ifPresent(item -> {
                    item.setUsedCount(usedCount);
                    userFeatureQuotaMapper.updateById(item);
                });
    }

    /**
     * 管理员直接切换用户会员方案，同时同步用户角色。
     */
    @Transactional
    public void assignMembershipPlanByAdmin(Long userId, String role, String planCode) {
        assignMembershipPlan(userId, planCode, "admin_console");
        syncUserRole(userId, planCode);
    }

    private void syncUserRole(Long userId, String planCode) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || "admin".equalsIgnoreCase(user.getRole())) {
            return;
        }
        String targetRole = "vip".equals(planCode) ? "vip" : "normal";
        if (!targetRole.equalsIgnoreCase(user.getRole())) {
            user.setRole(targetRole);
            userMapper.updateById(user);
        }
    }

    @Transactional
    public void assignMembershipPlan(Long userId, String planCode, String source) {
        MembershipPlanDO targetPlan = getPlanByCode(planCode);

        List<UserMembershipDO> memberships = userMembershipMapper.selectList(
                new LambdaQueryWrapper<UserMembershipDO>()
                        .eq(UserMembershipDO::getUserId, userId)
                        .eq(UserMembershipDO::getStatus, "active")
        );
        for (UserMembershipDO membership : memberships) {
            membership.setStatus("expired");
            membership.setEndAt(LocalDateTime.now());
            userMembershipMapper.updateById(membership);
        }

        UserMembershipDO created = new UserMembershipDO();
        created.setUserId(userId);
        created.setPlanCode(targetPlan.getPlanCode());
        created.setStatus("active");
        created.setAutoRenew(Boolean.FALSE);
        created.setSource(source == null || source.isBlank() ? "system" : source.trim());
        created.setStartAt(LocalDateTime.now());
        userMembershipMapper.insert(created);

        userFeatureQuotaMapper.delete(
                new LambdaQueryWrapper<UserFeatureQuotaDO>()
                        .eq(UserFeatureQuotaDO::getUserId, userId)
        );
        List<UserFeatureQuotaDO> createdQuotas = buildQuotaRows(userId, targetPlan);
        for (UserFeatureQuotaDO quota : createdQuotas) {
            userFeatureQuotaMapper.insert(quota);
        }
    }

    private void refreshExpiredQuotas(List<UserFeatureQuotaDO> quotas) {
        LocalDateTime now = LocalDateTime.now();
        for (UserFeatureQuotaDO quota : quotas) {
            if (!"daily".equals(quota.getPeriodType())) {
                continue;
            }
            if (quota.getResetAt() != null && quota.getResetAt().isBefore(now)) {
                quota.setUsedCount(0);
                quota.setResetAt(now.toLocalDate().plusDays(1).atStartOfDay());
                userFeatureQuotaMapper.updateById(quota);
            }
        }
    }

    private UserMembershipDO ensureMembership(Long userId, String role) {
        UserMembershipDO membership = userMembershipMapper.selectOne(
                new LambdaQueryWrapper<UserMembershipDO>()
                        .eq(UserMembershipDO::getUserId, userId)
                        .eq(UserMembershipDO::getStatus, "active")
                        .orderByDesc(UserMembershipDO::getId)
                        .last("limit 1")
        );
        if (membership != null) {
            return membership;
        }

        UserMembershipDO created = new UserMembershipDO();
        created.setUserId(userId);
        created.setPlanCode(resolveDefaultPlanCode(role));
        created.setStatus("active");
        created.setAutoRenew(Boolean.FALSE);
        created.setSource("system_default");
        created.setStartAt(LocalDateTime.now());
        userMembershipMapper.insert(created);
        return created;
    }

    private List<UserFeatureQuotaDO> ensureQuotaRecords(Long userId, MembershipPlanDO plan) {
        List<UserFeatureQuotaDO> quotas = userFeatureQuotaMapper.selectList(
                new LambdaQueryWrapper<UserFeatureQuotaDO>()
                        .eq(UserFeatureQuotaDO::getUserId, userId)
        );
        if (!quotas.isEmpty()) {
            return quotas;
        }

        List<UserFeatureQuotaDO> created = buildQuotaRows(userId, plan);
        for (UserFeatureQuotaDO item : created) {
            userFeatureQuotaMapper.insert(item);
        }
        return created;
    }

    private List<UserFeatureQuotaDO> buildQuotaRows(Long userId, MembershipPlanDO plan) {
        try {
            Map<String, Integer> quotas = objectMapper.readValue(
                    plan.getQuotaJson(),
                    new TypeReference<Map<String, Integer>>() {
                    }
            );
            List<UserFeatureQuotaDO> result = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : quotas.entrySet()) {
                UserFeatureQuotaDO quota = new UserFeatureQuotaDO();
                quota.setUserId(userId);
                quota.setFeatureCode(entry.getKey());
                quota.setLimitCount(entry.getValue());
                quota.setUsedCount(0);
                quota.setPeriodType(entry.getKey().contains("daily") ? "daily" : "permanent");
                quota.setResetAt(quota.getPeriodType().equals("daily")
                        ? LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay()
                        : null);
                result.add(quota);
            }
            return result;
        } catch (Exception e) {
            throw new BusinessException("会员方案配额配置损坏，无法初始化", e);
        }
    }

    private MembershipPlanDO getPlanByCode(String planCode) {
        MembershipPlanDO plan = membershipPlanMapper.selectOne(
                new LambdaQueryWrapper<MembershipPlanDO>()
                        .eq(MembershipPlanDO::getPlanCode, planCode)
                        .last("limit 1")
        );
        if (plan == null) {
            throw new BusinessException("会员方案不存在：" + planCode);
        }
        return plan;
    }

    private String resolveDefaultPlanCode(String role) {
        if ("vip".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role)) {
            return "vip";
        }
        return "free";
    }
}

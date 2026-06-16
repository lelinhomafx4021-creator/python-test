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
     * <p>
     * 根据用户角色自动分配默认方案：vip/admin 得 vip 方案，其他得 free 方案。
     *
     * @param userId 用户 ID
     * @param role   用户角色（normal/vip/admin）
     * @return 会员方案信息视图
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
     * 获取当前用户全部功能配额，同时自动刷新过期的每日配额。
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return 各功能编码的配额视图列表
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
     * 获取单个功能的配额上限。
     * <p>
     * 先确保会员和配额记录存在，再刷新过期配额，最后返回指定功能的限额。
     *
     * @param userId      用户 ID
     * @param role        用户角色
     * @param featureCode 功能编码（如 daily_ai_chat）
     * @return 该功能的配额上限值，未配置则返回 0
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
     * 同步永久型配额的已用数量（外部调用）。
     * <p>
     * 例如 AI 对话完成后，回调此方法更新已用次数。
     *
     * @param userId      用户 ID
     * @param role        用户角色
     * @param featureCode 功能编码
     * @param usedCount   最新的已用数量
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
     * <p>
     * 仅管理员可调用。将用户现有 active 会员置为 expired 后分配新方案。
     *
     * @param userId   目标用户 ID
     * @param role     管理员角色（校验用）
     * @param planCode 目标方案编码（如 free / vip）
     */
    @Transactional
    public void assignMembershipPlanByAdmin(Long userId, String role, String planCode) {
        assignMembershipPlan(userId, planCode, "admin_console");
        syncUserRole(userId, planCode);
    }

    /**
     * 根据方案编码同步用户角色。
     * <p>
     * vip 方案对应 vip 角色，其余方案对应 normal 角色。admin 角色用户不被降级。
     *
     * @param userId   用户 ID
     * @param planCode 会员方案编码
     */
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

    /**
     * 分配会员方案。
     * <p>
     * 将用户现有 active 会员置为 expired，创建新会员记录，
     * 并删除旧配额后按新方案的 quotaJson 重建配额。
     *
     * @param userId   用户 ID
     * @param planCode 目标方案编码
     * @param source   来源标识（如 vip_application / admin_console / system）
     */
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

    /**
     * 刷新过期的每日配额。
     * <p>
     * 对于 periodType=daily 且 resetAt 已过期的配额，将 usedCount 重置为 0
     * 并将 resetAt 推迟到次日零点。
     *
     * @param quotas 待检查的配额列表（会被原地修改并更新到数据库）
     */
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

    /**
     * 确保用户存在活跃的会员记录。
     * <p>
     * 若不存在则根据角色自动创建默认会员：vip/admin → vip 方案，其他 → free 方案。
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return 用户当前的活跃会员记录（已存在或新创建的）
     */
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

    /**
     * 确保用户存在配额记录。
     * <p>
     * 若配额记录为空，则根据方案配置的 quotaJson 自动初始化。
     *
     * @param userId 用户 ID
     * @param plan   会员方案实体（含配额 JSON 配置）
     * @return 用户的配额记录列表
     */
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

    /**
     * 根据方案的 quotaJson 构建配额记录列表。
     * <p>
     * 解析 JSON 中的功能编码→限额映射，自动判断周期类型：
     * 含 daily 关键字的为每日配额，其余为永久配额。
     *
     * @param userId 用户 ID
     * @param plan   会员方案实体（含 quotaJson 字段）
     * @return 新建的配额实体列表（尚未入库）
     * @throws BusinessException 当 quotaJson 解析失败时
     */
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

    /**
     * 根据方案编码查询会员方案。
     *
     * @param planCode 方案编码（如 free / vip）
     * @return 方案实体
     * @throws BusinessException 当方案编码不存在时
     */
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

    /**
     * 根据角色推断默认方案编码。
     *
     * @param role 用户角色（normal/vip/admin）
     * @return vip/admin 返回 "vip"，其他返回 "free"
     */
    private String resolveDefaultPlanCode(String role) {
        if ("vip".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role)) {
            return "vip";
        }
        return "free";
    }
}

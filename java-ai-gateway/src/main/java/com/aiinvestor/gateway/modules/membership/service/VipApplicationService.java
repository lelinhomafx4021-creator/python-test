package com.aiinvestor.gateway.modules.membership.service;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserMapper;
import com.aiinvestor.gateway.modules.membership.dao.entity.VipApplicationDO;
import com.aiinvestor.gateway.modules.membership.dao.mapper.VipApplicationMapper;
import com.aiinvestor.gateway.modules.membership.dto.VipApplicationReviewRequest;
import com.aiinvestor.gateway.modules.membership.vo.VipApplicationSubmitVO;
import com.aiinvestor.gateway.modules.membership.vo.VipApplicationVO;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * VIP 申请服务。
 * <p>
 * 负责 VIP 申请的完整生命周期：提交申请、管理员查看申请列表、审核（通过/驳回）。
 * 审核通过后自动分配 VIP 会员方案并升级用户角色。
 */
@Service
public class VipApplicationService {

    /** 合法的审核动作集合 */
    private static final Set<String> REVIEW_ACTIONS = Set.of("approve", "reject");

    private final VipApplicationMapper vipApplicationMapper;
    private final MembershipService membershipService;
    private final UserMapper userMapper;

    public VipApplicationService(VipApplicationMapper vipApplicationMapper,
                                 MembershipService membershipService,
                                 UserMapper userMapper) {
        this.vipApplicationMapper = vipApplicationMapper;
        this.membershipService = membershipService;
        this.userMapper = userMapper;
    }

    /**
     * 提交 VIP 申请。
     * <p>
     * 校验用户是否已有待审核的申请，避免重复提交。
     *
     * @param userId          申请人用户 ID
     * @param username        申请人用户名
     * @param paymentAmount   付款金额
     * @param paymentNote     付款备注
     * @param paymentProofUrl 付款凭证 URL
     * @return 提交成功的申请摘要
     * @throws BusinessException 当用户未登录、未上传凭证或已有待审核申请时
     */
    @Transactional
    public VipApplicationSubmitVO submit(Long userId,
                                         String username,
                                         double paymentAmount,
                                         String paymentNote,
                                         String paymentProofUrl) {
        if (userId == null) {
            throw new BusinessException("请先登录后再提交 VIP 申请");
        }
        if (paymentProofUrl == null || paymentProofUrl.trim().isEmpty()) {
            throw new BusinessException("请先上传付款凭证");
        }

        long pendingCount = vipApplicationMapper.selectCount(
                new LambdaQueryWrapper<VipApplicationDO>()
                        .eq(VipApplicationDO::getUserId, userId)
                        .eq(VipApplicationDO::getStatus, "pending")
        );
        if (pendingCount > 0) {
            throw new BusinessException("您已有待审核的 VIP 申请，请等待管理员处理");
        }

        VipApplicationDO application = new VipApplicationDO();
        application.setUserId(userId);
        application.setUsername(username == null || username.isBlank() ? "unknown" : username.trim());
        application.setPaymentAmount(BigDecimal.valueOf(paymentAmount));
        application.setPaymentNote(trimToEmpty(paymentNote));
        application.setPaymentScreenshot(paymentProofUrl.trim());
        application.setStatus("pending");
        application.setRejectReason("");
        vipApplicationMapper.insert(application);

        return new VipApplicationSubmitVO(
                application.getId(),
                application.getStatus(),
                application.getPaymentScreenshot()
        );
    }

    /**
     * 查看 VIP 申请列表（仅管理员）。
     * <p>
     * 同时加载审核人的用户名，避免 N+1 查询。
     *
     * @param status 可选的状态筛选，为空则返回全部
     * @return 申请详情列表，按创建时间倒序
     * @throws BusinessException 当非管理员调用时
     */
    public List<VipApplicationVO> listApplications(String status) {
        assertAdmin();

        LambdaQueryWrapper<VipApplicationDO> wrapper = new LambdaQueryWrapper<VipApplicationDO>()
                .orderByDesc(VipApplicationDO::getCreatedAt);
        if (status != null && !status.isBlank()) {
            wrapper.eq(VipApplicationDO::getStatus, status.trim().toLowerCase());
        }

        List<VipApplicationDO> applications = vipApplicationMapper.selectList(wrapper);
        if (applications.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> reviewerIds = applications.stream()
                .map(VipApplicationDO::getReviewedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserDO> reviewerMap = reviewerIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(reviewerIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity()));

        return applications.stream()
                .map(item -> toVO(item, reviewerMap.get(item.getReviewedBy())))
                .toList();
    }

    /**
     * 审核 VIP 申请（仅管理员）。
     * <p>
     * 通过时自动分配 VIP 会员方案并升级用户角色；
     * 驳回时必须填写原因。已处理（非 pending）的申请不可重复审核。
     *
     * @param appId   申请 ID
     * @param request 审核请求，含 action（approve/reject）和可选的 rejectReason
     * @return 审核后的申请详情
     * @throws BusinessException 当非管理员调用、申请不存在、申请已处理或驳回时未填写原因
     */
    @Transactional
    public VipApplicationVO review(Long appId, VipApplicationReviewRequest request) {
        assertAdmin();

        String action = normalizeAction(request.getAction());
        VipApplicationDO application = vipApplicationMapper.selectById(appId);
        if (application == null) {
            throw new BusinessException("VIP 申请不存在");
        }
        if (!"pending".equalsIgnoreCase(application.getStatus())) {
            throw new BusinessException("该 VIP 申请已处理，请勿重复审核");
        }

        UserDO reviewer = UserContext.get();
        application.setReviewedBy(reviewer != null ? reviewer.getId() : null);
        application.setReviewedAt(LocalDateTime.now());

        if ("approve".equals(action)) {
            application.setStatus("approved");
            application.setRejectReason("");
            vipApplicationMapper.updateById(application);
            membershipService.assignMembershipPlan(application.getUserId(), "vip", "vip_application");
            upgradeUserRoleToVip(application.getUserId());
        } else {
            String rejectReason = trimToEmpty(request.getRejectReason());
            if (rejectReason.isEmpty()) {
                throw new BusinessException("驳回时请填写原因");
            }
            application.setStatus("rejected");
            application.setRejectReason(rejectReason);
            vipApplicationMapper.updateById(application);
        }

        UserDO reviewedByUser = application.getReviewedBy() == null
                ? null
                : userMapper.selectById(application.getReviewedBy());
        return toVO(application, reviewedByUser);
    }

    /**
     * 将用户角色升级为 vip。
     * <p>
     * admin 角色用户不会被降级，已是 vip 的用户跳过。
     *
     * @param userId 用户 ID
     * @throws BusinessException 当用户不存在时
     */
    private void upgradeUserRoleToVip(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("申请用户不存在，无法开通 VIP");
        }
        if ("admin".equalsIgnoreCase(user.getRole()) || "vip".equalsIgnoreCase(user.getRole())) {
            return;
        }
        user.setRole("vip");
        userMapper.updateById(user);
    }

    /**
     * 将数据库实体转换为视图对象。
     *
     * @param item     数据库实体
     * @param reviewer 审核人用户实体（可能为 null）
     * @return 视图对象，含审核人用户名
     */
    private VipApplicationVO toVO(VipApplicationDO item, UserDO reviewer) {
        return new VipApplicationVO(
                item.getId(),
                item.getUserId(),
                item.getUsername(),
                item.getPaymentAmount(),
                item.getPaymentNote(),
                item.getPaymentScreenshot(),
                item.getStatus(),
                item.getRejectReason(),
                item.getReviewedBy(),
                reviewer != null ? reviewer.getUsername() : null,
                item.getReviewedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    /**
     * 断言当前用户为管理员，否则抛出业务异常。
     *
     * @throws BusinessException 当用户未登录或非管理员角色时
     */
    private void assertAdmin() {
        UserDO currentUser = UserContext.get();
        if (currentUser == null || !"admin".equalsIgnoreCase(currentUser.getRole())) {
            throw new BusinessException("仅管理员可访问 VIP 审核接口");
        }
    }

    /**
     * 规范化并校验审核动作。
     *
     * @param action 原始审核动作字符串
     * @return 小写规范化的动作值（approve 或 reject）
     * @throws BusinessException 当动作不是 approve 或 reject 时
     */
    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase();
        if (!REVIEW_ACTIONS.contains(normalized)) {
            throw new BusinessException("审核动作仅支持 approve 或 reject");
        }
        return normalized;
    }

    /**
     * 安全地将字符串 trim，null 返回空串。
     *
     * @param value 原始字符串
     * @return trim 后的字符串（null 时返回 ""）
     */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

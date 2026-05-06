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

@Service
public class VipApplicationService {

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

    private void assertAdmin() {
        UserDO currentUser = UserContext.get();
        if (currentUser == null || !"admin".equalsIgnoreCase(currentUser.getRole())) {
            throw new BusinessException("仅管理员可访问 VIP 审核接口");
        }
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase();
        if (!REVIEW_ACTIONS.contains(normalized)) {
            throw new BusinessException("审核动作仅支持 approve 或 reject");
        }
        return normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

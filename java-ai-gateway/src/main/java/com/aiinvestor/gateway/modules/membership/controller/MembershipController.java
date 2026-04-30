package com.aiinvestor.gateway.modules.membership.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.membership.service.MembershipService;
import com.aiinvestor.gateway.modules.membership.vo.FeatureQuotaVO;
import com.aiinvestor.gateway.modules.membership.vo.MembershipInfoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员域控制器。
 */
@RestController
@RequestMapping("/api/v1")
@LoginRequired
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /**
     * 获取当前会员信息。
     */
    @GetMapping("/memberships/me")
    public ApiResult<MembershipInfoVO> membership() {
        return ApiResult.ok(
                membershipService.getCurrentMembership(UserContext.getUserId(), UserContext.get().getRole())
        );
    }

    /**
     * 获取当前用户配额。
     */
    @GetMapping("/quotas/me")
    public ApiResult<List<FeatureQuotaVO>> quotas() {
        return ApiResult.ok(
                membershipService.listQuotas(UserContext.getUserId(), UserContext.get().getRole())
        );
    }
}

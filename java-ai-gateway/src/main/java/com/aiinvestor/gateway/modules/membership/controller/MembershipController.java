package com.aiinvestor.gateway.modules.membership.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.membership.service.MembershipService;
import com.aiinvestor.gateway.modules.membership.vo.FeatureQuotaVO;
import com.aiinvestor.gateway.modules.membership.vo.MembershipInfoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 会员域控制器。
 */
@RestController
@RequestMapping("/api/v1")
@LoginRequired
@Tag(name = "会员体系", description = "会员信息查询、功能配额查询")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /** 获取当前会员信息。 */
    @Operation(summary = "获取会员信息", description = "获取当前用户的会员等级和有效期等信息")
    @GetMapping("/memberships/me")
    public ApiResult<MembershipInfoVO> membership() {
        return ApiResult.ok(
                membershipService.getCurrentMembership(UserContext.getUserId(), UserContext.get().getRole())
        );
    }

    /** 获取当前用户配额。 */
    @Operation(summary = "获取功能配额", description = "获取当前用户各项功能（AI对话、行情查询等）的使用配额")
    @GetMapping("/quotas/me")
    public ApiResult<List<FeatureQuotaVO>> quotas() {
        return ApiResult.ok(
                membershipService.listQuotas(UserContext.getUserId(), UserContext.get().getRole())
        );
    }
}

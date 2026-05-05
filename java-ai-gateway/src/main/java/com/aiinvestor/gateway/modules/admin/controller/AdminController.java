package com.aiinvestor.gateway.modules.admin.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateMembershipRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateTicketStatusRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateUserRoleRequest;
import com.aiinvestor.gateway.modules.admin.service.AdminService;
import com.aiinvestor.gateway.modules.admin.vo.AdminDashboardVO;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminUserPortfolioVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminUserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 管理端控制器。
 */
@RestController
@RequestMapping("/api/v1/admin")
@LoginRequired
@Tag(name = "管理后台", description = "管理员后台：用户管理、工单处理、会员修改")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 查询管理端首页总览。 */
    @Operation(summary = "管理端总览", description = "获取管理后台的首页数据总览（用户数、订单数等）")
    @GetMapping("/overview")
    public ApiResult<AdminDashboardVO> overview() {
        return ApiResult.ok(adminService.getDashboard());
    }

    /** 查询用户列表。 */
    @Operation(summary = "查询用户列表", description = "按关键词搜索用户或获取全部用户列表")
    @GetMapping("/users")
    public ApiResult<List<AdminUserVO>> users(
            @Parameter(description = "搜索关键词（用户名/昵称）", required = false)
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(adminService.listUsers(keyword));
    }

    /** 查询人工工单列表。 */
    @Operation(summary = "查询人工工单", description = "按状态筛选人工兜底工单列表")
    @GetMapping("/tickets")
    public ApiResult<List<HandoffTicketVO>> tickets(
            @Parameter(description = "工单状态（待处理/处理中/已完成）", required = false)
            @RequestParam(required = false) String status) {
        return ApiResult.ok(adminService.listHandoffTickets(status));
    }

    /** 查询指定用户持仓。 */
    @Operation(summary = "查询用户持仓", description = "查看指定用户的模拟交易持仓详情")
    @GetMapping("/users/{userId}/portfolio")
    public ApiResult<AdminUserPortfolioVO> userPortfolio(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
                                                          @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResult.ok(adminService.getUserPortfolio(userId, refresh));
    }

    /** 修改用户角色。 */
    @Operation(summary = "修改用户角色", description = "修改指定用户的角色（普通用户/会员用户等）")
    @PutMapping("/users/{userId}/role")
    public ApiResult<Boolean> updateUserRole(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
                                             @Valid @RequestBody AdminUpdateUserRoleRequest request) {
        adminService.updateUserRole(userId, request);
        return ApiResult.ok(Boolean.TRUE);
    }

    /** 修改用户会员方案。 */
    @Operation(summary = "修改用户会员", description = "修改指定用户的会员等级和有效期")
    @PutMapping("/users/{userId}/membership")
    public ApiResult<Boolean> updateMembership(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
                                               @Valid @RequestBody AdminUpdateMembershipRequest request) {
        adminService.updateMembership(userId, request);
        return ApiResult.ok(Boolean.TRUE);
    }

    /** 修改工单状态。 */
    @Operation(summary = "处理工单", description = "更新人工工单的处理状态")
    @PutMapping("/tickets/{traceId}/status")
    public ApiResult<HandoffTicketVO> updateTicketStatus(
            @Parameter(description = "工单追踪ID（traceId）", required = true)
            @PathVariable String traceId,
                                                              @Valid @RequestBody AdminUpdateTicketStatusRequest request) {
        return ApiResult.ok(adminService.updateTicketStatus(traceId, request));
    }
}

package com.aiinvestor.gateway.modules.admin.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateMembershipRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateTicketStatusRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateUserRoleRequest;
import com.aiinvestor.gateway.modules.admin.service.AdminService;
import com.aiinvestor.gateway.modules.admin.vo.AdminDashboardVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminHandoffTicketVO;
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

import java.util.List;

/**
 * 管理端控制器。
 */
@RestController
@RequestMapping("/api/v1/admin")
@LoginRequired
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 查询管理端首页总览。
     */
    @GetMapping("/overview")
    public ApiResult<AdminDashboardVO> overview() {
        return ApiResult.ok(adminService.getDashboard());
    }

    /**
     * 查询用户列表。
     */
    @GetMapping("/users")
    public ApiResult<List<AdminUserVO>> users(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(adminService.listUsers(keyword));
    }

    /**
     * 查询人工工单列表。
     */
    @GetMapping("/tickets")
    public ApiResult<List<AdminHandoffTicketVO>> tickets(@RequestParam(required = false) String status) {
        return ApiResult.ok(adminService.listHandoffTickets(status));
    }

    /**
     * 查询指定用户持仓。
     */
    @GetMapping("/users/{userId}/portfolio")
    public ApiResult<AdminUserPortfolioVO> userPortfolio(@PathVariable Long userId,
                                                          @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResult.ok(adminService.getUserPortfolio(userId, refresh));
    }

    /**
     * 修改用户角色。
     */
    @PutMapping("/users/{userId}/role")
    public ApiResult<Boolean> updateUserRole(@PathVariable Long userId,
                                             @Valid @RequestBody AdminUpdateUserRoleRequest request) {
        adminService.updateUserRole(userId, request);
        return ApiResult.ok(Boolean.TRUE);
    }

    /**
     * 修改用户会员方案。
     */
    @PutMapping("/users/{userId}/membership")
    public ApiResult<Boolean> updateMembership(@PathVariable Long userId,
                                               @Valid @RequestBody AdminUpdateMembershipRequest request) {
        adminService.updateMembership(userId, request);
        return ApiResult.ok(Boolean.TRUE);
    }

    /**
     * 修改工单状态。
     */
    @PutMapping("/tickets/{traceId}/status")
    public ApiResult<AdminHandoffTicketVO> updateTicketStatus(@PathVariable String traceId,
                                                              @Valid @RequestBody AdminUpdateTicketStatusRequest request) {
        return ApiResult.ok(adminService.updateTicketStatus(traceId, request));
    }
}

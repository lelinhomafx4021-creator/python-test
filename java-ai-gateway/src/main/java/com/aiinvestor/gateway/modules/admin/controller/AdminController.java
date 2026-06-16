package com.aiinvestor.gateway.modules.admin.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.annotation.RequireAdmin;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateMembershipRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateTicketStatusRequest;
import com.aiinvestor.gateway.modules.admin.dto.AdminUpdateUserRoleRequest;
import com.aiinvestor.gateway.modules.admin.dto.AnnouncementDTO;
import com.aiinvestor.gateway.modules.admin.service.AdminService;
import com.aiinvestor.gateway.modules.admin.vo.AdminDashboardVO;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminUserPortfolioVO;
import com.aiinvestor.gateway.modules.admin.vo.AdminUserVO;
import com.aiinvestor.gateway.modules.shared.service.AnnouncementService;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.AnnouncementVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * 管理后台控制器。
 * <p>
 * 提供管理员专属功能：数据总览、用户管理（列表/角色/会员）、
 * 工单处理（查询/状态更新）、公告管理（增删改查发布）。
 * 所有接口需要登录且校验 admin 角色（部分接口双重校验）。
 *
 * @see com.aiinvestor.gateway.modules.admin.service.AdminService
 */
@RestController
@RequestMapping("/api/v1/admin")
@LoginRequired
@RequireAdmin
@Tag(name = "管理后台", description = "管理员后台：用户管理、工单处理、会员修改")
public class AdminController {

    private final AdminService adminService;
    private final AnnouncementService announcementService;

    /**
     * @param adminService        管理端业务服务
     * @param announcementService 公告业务服务
     */
    public AdminController(AdminService adminService, AnnouncementService announcementService) {
        this.adminService = adminService;
        this.announcementService = announcementService;
    }

    /**
     * 获取管理后台首页数据总览。
     * <p>
     * 返回用户数、会员数、会话数、工单数等 9 项核心指标。
     *
     * @return 仪表盘总览数据
     */
    @Operation(summary = "管理端总览", description = "获取管理后台的首页数据总览（用户数、订单数等）")
    @GetMapping("/overview")
    public ApiResult<AdminDashboardVO> overview() {
        return ApiResult.ok(adminService.getDashboard());
    }

    /**
     * 查询用户列表（支持关键词模糊搜索）。
     * <p>
     * 最大返回 200 条，按 ID 倒序。
     *
     * @param keyword 搜索关键词（匹配用户名/昵称/手机号），null 时返回全部
     * @return 用户列表
     */
    @Operation(summary = "查询用户列表", description = "按关键词搜索用户或获取全部用户列表")
    @GetMapping("/users")
    public ApiResult<List<AdminUserVO>> users(
            @Parameter(description = "搜索关键词（用户名/昵称）", required = false)
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(adminService.listUsers(keyword));
    }

    /**
     * 查询人工兜底工单列表（支持按状态筛选）。
     * <p>
     * 最大返回 200 条，按创建时间倒序。
     *
     * @param status 工单状态筛选（null 时返回全部）
     * @return 工单列表
     */
    @Operation(summary = "查询人工工单", description = "按状态筛选人工兜底工单列表")
    @GetMapping("/tickets")
    public ApiResult<List<HandoffTicketVO>> tickets(
            @Parameter(description = "工单状态（待处理/处理中/已完成）", required = false)
            @RequestParam(required = false) String status) {
        return ApiResult.ok(adminService.listHandoffTickets(status));
    }

    /**
     * 查询指定用户的模拟交易持仓详情。
     *
     * @param userId  目标用户 ID
     * @param refresh 是否强制刷新实时行情（默认 false，使用缓存）
     * @return 用户持仓组合视图
     */
    @Operation(summary = "查询用户持仓", description = "查看指定用户的模拟交易持仓详情")
    @GetMapping("/users/{userId}/portfolio")
    public ApiResult<AdminUserPortfolioVO> userPortfolio(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
                                                          @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResult.ok(adminService.getUserPortfolio(userId, refresh));
    }

    /**
     * 修改指定用户的角色。
     *
     * @param userId  目标用户 ID
     * @param request 新角色（guest/normal/vip/admin）
     * @return 操作成功
     */
    @Operation(summary = "修改用户角色", description = "修改指定用户的角色（普通用户/会员用户等）")
    @PutMapping("/users/{userId}/role")
    public ApiResult<Boolean> updateUserRole(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
                                             @Valid @RequestBody AdminUpdateUserRoleRequest request) {
        adminService.updateUserRole(userId, request);
        return ApiResult.ok(Boolean.TRUE);
    }

    /**
     * 修改指定用户的会员方案。
     * <p>
     * 会为新方案创建对应的功能配额，同时清理旧方案残留。
     *
     * @param userId  目标用户 ID
     * @param request 新会员方案编码（guest/normal/vip）
     * @return 操作成功
     */
    @Operation(summary = "修改用户会员", description = "修改指定用户的会员等级和有效期")
    @PutMapping("/users/{userId}/membership")
    public ApiResult<Boolean> updateMembership(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
                                               @Valid @RequestBody AdminUpdateMembershipRequest request) {
        adminService.updateMembership(userId, request);
        return ApiResult.ok(Boolean.TRUE);
    }

    /**
     * 更新人工工单的处理状态。
     * <p>
     * 管理员可填写处理备注（仅后台可见）和回复消息（用户可见），
     * 处理完成后状态变为 closed。
     *
     * @param traceId 工单追踪 ID（traceId）
     * @param request 新状态及处理信息
     * @return 更新后的工单视图
     */
    @Operation(summary = "处理工单", description = "更新人工工单的处理状态")
    @PutMapping("/tickets/{traceId}/status")
    public ApiResult<HandoffTicketVO> updateTicketStatus(
            @Parameter(description = "工单追踪ID（traceId）", required = true)
            @PathVariable String traceId,
                                                              @Valid @RequestBody AdminUpdateTicketStatusRequest request) {
        return ApiResult.ok(adminService.updateTicketStatus(traceId, request));
    }

    // ========== 公告管理 ==========

    /**
     * 获取所有公告（含未发布的草稿）。
     *
     * @return 全部公告列表
     */
    @Operation(summary = "获取所有公告", description = "获取全部公告（含草稿）")
    @GetMapping("/announcements")
    public ApiResult<List<AnnouncementVO>> listAnnouncements() {
return ApiResult.ok(announcementService.listAll());
    }

    /**
     * 创建一条新公告（默认为草稿状态）。
     *
     * @param dto 公告信息（标题、内容、类型）
     * @return 创建后的公告视图
     */
    @Operation(summary = "创建公告", description = "新建一条公告")
    @PostMapping("/announcements")
    public ApiResult<AnnouncementVO> createAnnouncement(
            @Valid @RequestBody AnnouncementDTO dto) {
Long userId = UserContext.getUserId();
        return ApiResult.ok(announcementService.create(userId, dto.getTitle(), dto.getContent(), dto.getType()));
    }

    /**
     * 修改公告内容（标题、正文、类型可部分更新）。
     *
     * @param id  公告 ID
     * @param dto 更新信息（部分字段可为 null）
     * @return 更新后的公告视图
     */
    @Operation(summary = "更新公告", description = "修改公告内容")
    @PutMapping("/announcements/{id}")
    public ApiResult<AnnouncementVO> updateAnnouncement(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementDTO dto) {
return ApiResult.ok(announcementService.update(id, dto.getTitle(), dto.getContent(), dto.getType()));
    }

    /**
     * 发布公告：将草稿改为已发布状态，记录发布时间。
     *
     * @param id 公告 ID
     * @return 发布后的公告视图
     */
    @Operation(summary = "发布公告", description = "将公告状态改为已发布")
    @PutMapping("/announcements/{id}/publish")
    public ApiResult<AnnouncementVO> publishAnnouncement(@PathVariable Long id) {
return ApiResult.ok(announcementService.publish(id));
    }

    /**
     * 删除指定公告（物理删除，不可恢复）。
     *
     * @param id 公告 ID
     * @return 操作成功
     */
    @Operation(summary = "删除公告", description = "删除指定公告")
    @DeleteMapping("/announcements/{id}")
    public ApiResult<Boolean> deleteAnnouncement(@PathVariable Long id) {
return ApiResult.ok(announcementService.delete(id));
    }
}

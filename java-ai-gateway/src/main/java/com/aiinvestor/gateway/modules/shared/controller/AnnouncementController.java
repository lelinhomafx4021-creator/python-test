package com.aiinvestor.gateway.modules.shared.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.service.AnnouncementService;
import com.aiinvestor.gateway.modules.shared.vo.AnnouncementVO;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统公告控制器（用户端）。
 * <p>
 * 提供公告查询能力——仅展示已发布的公告。
 * 公告的增删改由管理端 AdminController 负责。
 * 所有接口需要登录。
 */
@RestController
@RequestMapping("/api/v1/announcements")
@LoginRequired
@Tag(name = "公告", description = "系统公告查询")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * @param announcementService 公告业务服务
     */
    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /**
     * 获取所有已发布公告。
     * <p>
     * 按发布时间倒序排列，仅返回 status=published 的数据。
     *
     * @return 已发布公告列表
     */
    @Operation(summary = "获取已发布公告", description = "获取所有已发布的系统公告")
    @GetMapping
    public ApiResult<List<AnnouncementVO>> listPublished() {
        return ApiResult.ok(announcementService.listPublished());
    }
}

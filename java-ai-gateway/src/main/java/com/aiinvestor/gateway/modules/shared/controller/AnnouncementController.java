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

@RestController
@RequestMapping("/api/v1/announcements")
@LoginRequired
@Tag(name = "公告", description = "系统公告查询")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @Operation(summary = "获取已发布公告", description = "获取所有已发布的系统公告")
    @GetMapping
    public ApiResult<List<AnnouncementVO>> listPublished() {
        return ApiResult.ok(announcementService.listPublished());
    }
}

package com.aiinvestor.gateway.modules.shared.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.shared.vo.UserNotificationVO;
import com.aiinvestor.gateway.modules.shared.service.UserNotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 用户通知控制器。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@LoginRequired
@Tag(name = "通知消息", description = "用户通知查询、标记已读")
public class NotificationController {

    private final UserNotificationService userNotificationService;

    public NotificationController(UserNotificationService userNotificationService) {
        this.userNotificationService = userNotificationService;
    }

    /** 查询当前用户通知。 */
    @Operation(summary = "查询通知列表", description = "获取当前用户的所有通知消息")
    @GetMapping
    public ApiResult<List<UserNotificationVO>> list() {
        return ApiResult.ok(userNotificationService.listMyNotifications(UserContext.getUserId()));
    }

    /** 标记通知已读。 */
    @Operation(summary = "标记通知已读", description = "将指定通知标记为已读状态")
    @PostMapping("/{id}/read")
    public ApiResult<Void> read(
            @Parameter(description = "通知ID", required = true)
            @PathVariable("id") Long notificationId) {
        userNotificationService.markRead(UserContext.getUserId(), notificationId);
        return ApiResult.ok(null);
    }
}

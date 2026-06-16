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
 * <p>
 * 提供当前登录用户的通知查询和已读标记功能。
 * 所有接口均需要登录（通过 {@link com.aiinvestor.gateway.modules.shared.annotation.LoginRequired} 标注）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@LoginRequired
@Tag(name = "通知消息", description = "用户通知查询、标记已读")
public class NotificationController {

    private final UserNotificationService userNotificationService;

    /**
     * 构造函数注入通知服务。
     *
     * @param userNotificationService 用户通知业务服务
     */
    public NotificationController(UserNotificationService userNotificationService) {
        this.userNotificationService = userNotificationService;
    }

    /**
     * 查询当前登录用户的通知列表（最近 20 条，按 ID 倒序）。
     *
     * @return 通知列表
     */
    @Operation(summary = "查询通知列表", description = "获取当前用户的所有通知消息")
    @GetMapping
    public ApiResult<List<UserNotificationVO>> list() {
        return ApiResult.ok(userNotificationService.listMyNotifications(UserContext.getUserId()));
    }

    /**
     * 将指定通知标记为已读。
     * <p>
     * 会校验通知归属，只能标记自己的通知。
     *
     * @param notificationId 通知 ID
     * @return 空响应（操作成功）
     */
    @Operation(summary = "标记通知已读", description = "将指定通知标记为已读状态")
    @PostMapping("/{id}/read")
    public ApiResult<Void> read(
            @Parameter(description = "通知ID", required = true)
            @PathVariable("id") Long notificationId) {
        userNotificationService.markRead(UserContext.getUserId(), notificationId);
        return ApiResult.ok(null);
    }
}

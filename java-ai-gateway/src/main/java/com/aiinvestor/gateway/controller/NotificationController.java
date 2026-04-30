package com.aiinvestor.gateway.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.model.vo.UserNotificationVO;
import com.aiinvestor.gateway.service.UserNotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户通知控制器。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@LoginRequired
public class NotificationController {

    private final UserNotificationService userNotificationService;

    public NotificationController(UserNotificationService userNotificationService) {
        this.userNotificationService = userNotificationService;
    }

    /**
     * 查询当前用户通知。
     */
    @GetMapping
    public ApiResult<List<UserNotificationVO>> list() {
        return ApiResult.ok(userNotificationService.listMyNotifications(UserContext.getUserId()));
    }

    /**
     * 标记通知已读。
     */
    @PostMapping("/{id}/read")
    public ApiResult<Void> read(@PathVariable("id") Long notificationId) {
        userNotificationService.markRead(UserContext.getUserId(), notificationId);
        return ApiResult.ok(null);
    }
}

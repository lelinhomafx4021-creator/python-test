package com.aiinvestor.gateway.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户通知视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationVO {

    /** 通知 ID。 */
    private Long id;

    /** 通知分类。 */
    private String category;

    /** 通知标题。 */
    private String title;

    /** 通知内容。 */
    private String content;

    /** 状态。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 阅读时间。 */
    private LocalDateTime readAt;
}

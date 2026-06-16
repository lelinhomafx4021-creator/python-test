package com.aiinvestor.gateway.modules.shared.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户通知视图对象（VO）。
 * <p>
 * 用于 API 响应，只暴露前端需要的字段，隐藏数据库内部细节。
 * 与 {@link com.aiinvestor.gateway.modules.shared.dao.entity.UserNotificationDO} 一一对应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationVO {

    /** 通知 ID。 */
    private Long id;

    /** 通知分类（system/order/trade/ticket）。 */
    private String category;

    /** 通知标题。 */
    private String title;

    /** 通知正文内容。 */
    private String content;

    /** 读取状态：unread / read。 */
    private String status;

    /** 通知创建时间。 */
    private LocalDateTime createdAt;

    /** 阅读时间，未读时为 null。 */
    private LocalDateTime readAt;
}

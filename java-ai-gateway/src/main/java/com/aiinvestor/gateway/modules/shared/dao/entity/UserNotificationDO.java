package com.aiinvestor.gateway.modules.shared.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户通知记录实体。
 * <p>
 * 对应数据库 user_notifications 表，存储系统推送给用户的消息通知，
 * 例如订单成交提醒、系统公告、工单处理结果等。
 */
@Data
@TableName("user_notifications")
public class UserNotificationDO {

    /** 主键，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收通知的用户 ID。 */
    private Long userId;

    /** 通知分类（system/order/trade/ticket）。 */
    private String category;

    /** 通知标题。 */
    private String title;

    /** 通知正文内容。 */
    private String content;

    /** 读取状态：unread（未读）/ read（已读）。 */
    private String status;

    /** 通知创建时间。 */
    private LocalDateTime createdAt;

    /** 用户点击已读的时间，未读时为 null。 */
    private LocalDateTime readAt;
}

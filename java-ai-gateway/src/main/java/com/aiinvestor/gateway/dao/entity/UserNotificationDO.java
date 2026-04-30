package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户通知记录实体。
 */
@Data
@TableName("user_notifications")
public class UserNotificationDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 通知分类。 */
    private String category;

    /** 通知标题。 */
    private String title;

    /** 通知内容。 */
    private String content;

    /** 读取状态。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 读取时间。 */
    private LocalDateTime readAt;
}

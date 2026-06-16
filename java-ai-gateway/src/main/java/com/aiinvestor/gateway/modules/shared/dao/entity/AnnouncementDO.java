package com.aiinvestor.gateway.modules.shared.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告实体，对应数据库 announcements 表。
 * <p>
 * 公告有两种状态：draft（草稿）和 published（已发布）。
 * 仅已发布状态的公告会展示给普通用户。
 */
@Data
@TableName("announcements")
public class AnnouncementDO {

    /** 主键，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题。 */
    private String title;

    /** 公告正文内容。 */
    private String content;

    /** 公告类型（如 system/event/feature）。 */
    private String type;

    /** 公告状态：draft（草稿）/ published（已发布）。 */
    private String status;

    /** 正式发布时间，草稿状态下为 null。 */
    private LocalDateTime publishedAt;

    /** 创建者用户 ID。 */
    private Long createdBy;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
}

package com.aiinvestor.gateway.modules.shared.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统公告视图对象（VO）。
 * <p>
 * 用于 API 响应，仅包含前端需要的字段。
 * 与 {@link com.aiinvestor.gateway.modules.shared.dao.entity.AnnouncementDO} 一一对应，
 * 但不暴露 updatedAt 等内部字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementVO {

    /** 公告 ID。 */
    private Long id;

    /** 公告标题。 */
    private String title;

    /** 公告正文内容。 */
    private String content;

    /** 公告类型（system/event/feature）。 */
    private String type;

    /** 公告状态：draft / published。 */
    private String status;

    /** 正式发布时间，草稿状态下为 null。 */
    private LocalDateTime publishedAt;

    /** 创建者用户 ID。 */
    private Long createdBy;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}

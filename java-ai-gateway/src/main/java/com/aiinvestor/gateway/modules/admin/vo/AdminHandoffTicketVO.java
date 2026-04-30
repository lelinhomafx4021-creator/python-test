package com.aiinvestor.gateway.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端人工工单展示对象。
 */
@Data
public class AdminHandoffTicketVO {

    /** trace 标识。 */
    private String traceId;

    /** 用户 ID。 */
    private String userId;

    /** 用户名。 */
    private String username;

    /** 用户昵称。 */
    private String nickname;

    /** 会话 ID。 */
    private String sessionId;

    /** 用户问题。 */
    private String query;

    /** 转人工原因。 */
    private String handoffReason;

    /** 转人工摘要。 */
    private String handoffSummary;

    /** 工单状态。 */
    private String status;

    /** 管理员处理备注。 */
    private String processNote;

    /** 回复给用户的处理结果。 */
    private String responseMessage;

    /** 处理人用户名。 */
    private String handledBy;

    /** 处理时间。 */
    private LocalDateTime handledAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

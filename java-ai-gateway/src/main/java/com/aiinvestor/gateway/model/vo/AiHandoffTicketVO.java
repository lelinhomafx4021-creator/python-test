package com.aiinvestor.gateway.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工工单展示对象。
 * 用于前端查看 AI 转人工后的处理进度与回复信息。
 */
@Data
public class AiHandoffTicketVO {

    /** 本次对话请求的唯一追踪 ID。 */
    private String traceId;

    /** 会话 ID。 */
    private String sessionId;

    /** 用户原始问题。 */
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

    /** 建单时间。 */
    private LocalDateTime createdAt;
}

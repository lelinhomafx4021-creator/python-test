package com.aiinvestor.gateway.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工兜底工单展示对象。
 * 用于前端展示 AI 转人工后的工单摘要信息。
 */
@Data
public class AiHandoffTicketVO {

    /** 本次对话请求的唯一追踪 ID */
    private String traceId;
    /** 会话 ID，前端可据此定位原始会话 */
    private String sessionId;
    /** 用户原始问题 */
    private String query;
    /** 转人工原因 */
    private String handoffReason;
    /** 交给人工客服的摘要说明 */
    private String handoffSummary;
    /** 工单状态 */
    private String status;
    /** 建单时间 */
    private LocalDateTime createdAt;
}

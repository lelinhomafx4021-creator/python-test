package com.aiinvestor.gateway.modules.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 人工接管工单实体类。
 * <p>
 * 当 AI 智能体无法处理用户请求时，生成一条接管工单，
 * 由人工客服接手处理。对应数据库表 ai_handoff_tickets。
 */
@TableName("ai_handoff_tickets")
public class AiHandoffTicketDO {

    @TableId(type = IdType.AUTO)
    private Long id; // 主键ID，自增

    private String traceId; // 链路追踪ID，用于串联完整请求链路
    private Long userId; // 用户ID
    private String sessionId; // 会话ID，标识一次AI对话会话
    private String threadId; // 线程ID，标识对话中的线程
    private String query; // 用户原始提问内容
    private String handoffReason; // 转人工原因，如AI无法回答/用户主动要求
    private String handoffSummary; // 接管摘要，AI对问题的上下文总结
    private String status; // 工单状态：PENDING/PROCESSING/RESOLVED/CLOSED
    private String processNote; // 人工处理备注
    private String responseMessage; // 最终回复消息
    private String handledBy; // 处理人，工单被哪位客服/运维接手
    private LocalDateTime handledAt; // 工单处理时间
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 最后更新时间

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getHandoffReason() {
        return handoffReason;
    }

    public void setHandoffReason(String handoffReason) {
        this.handoffReason = handoffReason;
    }

    public String getHandoffSummary() {
        return handoffSummary;
    }

    public void setHandoffSummary(String handoffSummary) {
        this.handoffSummary = handoffSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessNote() {
        return processNote;
    }

    public void setProcessNote(String processNote) {
        this.processNote = processNote;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 聊天轮次实体（Java 业务侧主存储）。
 *
 * 说明：
 * - Python 不再存业务会话；会话和历史统一落 Java 库。
 * - 表结构对应 ai_chat_turns。
 */
@TableName("ai_chat_turns")
public class ChatTurnDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String sessionId;
    private String threadId;
    private String traceId;

    private String query;
    private String answer;
    private String intent;
    private String source;

    private Boolean reviewPassed;
    private String responseMode;
    private Integer a2aCount;
    private String title;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getReviewPassed() {
        return reviewPassed;
    }

    public void setReviewPassed(Boolean reviewPassed) {
        this.reviewPassed = reviewPassed;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public Integer getA2aCount() {
        return a2aCount;
    }

    public void setA2aCount(Integer a2aCount) {
        this.a2aCount = a2aCount;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

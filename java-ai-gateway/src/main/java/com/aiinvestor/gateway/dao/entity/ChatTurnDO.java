package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * ============================================================
 * 聊天轮次实体 (DO)
 * ============================================================
 *
 * 对应数据库表 ai_chat_turns。
 *
 * 架构决策（重要）：
 *   聊天历史全部由 Java 侧管理，Python 不存业务会话。
 *   - Python 只负责 AI 推理
 *   - Java 负责持久化和查询
 *   这种"写读分离"避免了 Python 和 Java 各存一份导致的数据不一致。
 *
 * 字段说明：
 *   - sessionId : 前端维护的会话 ID（同一会话下的多轮对话共享）
 *   - threadId  : 传给 LangGraph 的线程 ID（格式：userId:sessionId）
 *   - traceId   : 单次请求的唯一标识（用于日志追踪和回填答案）
 *   - title     : AI 自动生成的会话标题（异步更新，非实时）
 *
 * @author AI Investor Team
 */
@TableName("ai_chat_turns")
public class ChatTurnDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（关联 users 表） */
    private String userId;

    /** 会话 ID（前端维护） */
    private String sessionId;

    /** LangGraph 线程 ID（userId:sessionId） */
    private String threadId;

    /** 本次请求的追踪 ID（UUID） */
    private String traceId;

    /** 用户提问的内容 */
    private String query;

    /** AI 的回答内容（初始为"[思考中...]"，收到 final_answer 后回填） */
    private String answer;

    /** 意图识别结果（如 "investment" 表示投资分析） */
    private String intent;

    /** 回答数据来源标识（如 "python_stream"） */
    private String source;

    /** AI 回答是否通过幻觉检测 */
    private Boolean reviewPassed;

    /** 响应模式（如 "stream" 流式） */
    private String responseMode;

    /** Agent-to-Agent 对话轮数 */
    private Integer a2aCount;

    /** AI 生成的会话标题（异步回填） */
    private String title;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    // ===================== Getter/Setter =====================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getReviewPassed() { return reviewPassed; }
    public void setReviewPassed(Boolean reviewPassed) { this.reviewPassed = reviewPassed; }
    public String getResponseMode() { return responseMode; }
    public void setResponseMode(String responseMode) { this.responseMode = responseMode; }
    public Integer getA2aCount() { return a2aCount; }
    public void setA2aCount(Integer a2aCount) { this.a2aCount = a2aCount; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

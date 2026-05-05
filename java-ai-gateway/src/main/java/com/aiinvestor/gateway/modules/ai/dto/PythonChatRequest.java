package com.aiinvestor.gateway.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================
 * Java → Python AI 引擎的请求体
 *
 * 命名差异处理：
 *   Java 习惯驼峰命名（threadId），Python 习惯蛇形命名（thread_id）。
 *   通过 @JsonProperty 做映射，Jackson 序列化时自动转换。
 *
 * @author AI Investor Team
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PythonChatRequest {
    /** 用户输入的消息内容 */
    private String message;

    /**
     * LangGraph 的会话线程 ID。
     * 序列化为 JSON 时字段名为 "thread_id"（Python 命名风格）。
     */
    @JsonProperty("thread_id")
    private String threadId;

    /**
     * 全链路追踪 ID。
     * 序列化为 JSON 时字段名为 "trace_id"。
     * 用于关联 Java 日志和 Python 日志中的同一次请求。
     */
    @JsonProperty("trace_id")
    private String traceId;

    /**
     * 用户角色：决定 Python 端使用哪套图流程。
     * "normal"（普通用户）：精简流程，禁止买卖建议，省 Token
     * "vip"（VIP 用户）：完整流程，深度分析 + 投资建议
     * 序列化为 JSON 时字段名为 "role"。
     */
    private String role = "normal";
}

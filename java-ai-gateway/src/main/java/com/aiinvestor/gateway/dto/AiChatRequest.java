package com.aiinvestor.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ============================================================
 * AI 聊天请求体 - 前端发起的聊天请求
 * ============================================================
 *
 * 设计考量：
 *   前端只传 message 和 sessionId，不传 threadId。
 *   threadId 由 Java 网关根据 userId + sessionId 统一拼接（格式：userId:sessionId），
 *   避免前端随意构造 threadId 导致会话串线。
 *
 * @author AI Investor Team
 */
@Data
public class AiChatRequest {

    /** 用户输入的投资问题，不能为空 */
    @NotBlank
    private String message;

    /**
     * 业务会话 ID。
     * 由前端生成并维护（如 UUID），Java 侧不作变更。
     * 同一个 sessionId 下的所有对话为一个"会话"。
     */
    @NotBlank
    private String sessionId;
}

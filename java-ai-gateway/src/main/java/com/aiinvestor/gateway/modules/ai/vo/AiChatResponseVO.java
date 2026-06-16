package com.aiinvestor.gateway.modules.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标准化 AI 同步响应（Java 网关 -> 前端）。
 *
 * <p>与流式 SSE 响应不同，此 VO 用于同步场景（如标题生成、摘要请求）。
 * 流式聊天的最终答案通过 SSE 通道直接推送，不经过此对象。
 *
 * <p>使用场景：
 * <ul>
 *   <li>非流式的 AI 问答请求</li>
 *   <li>会话标题异步生成完成后的通知</li>
 *   <li>其他不需要实时流式输出的 AI 功能调用</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseVO {

    /** 会话 ID，前端用此 ID 关联对话上下文 */
    private String sessionId;

    /** 上下文类型（general / investment / trade） */
    private String contextType;

    /** 上下文引用（具体的分析对象标识） */
    private String contextRef;

    /** AI 的最终回答内容（Markdown 格式） */
    private String answer;
}

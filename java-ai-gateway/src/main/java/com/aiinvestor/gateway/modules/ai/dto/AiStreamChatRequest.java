package com.aiinvestor.gateway.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标准化 AI 流式聊天请求（前端 -> Java 网关）。
 *
 * <p>这是前端发送给 Java 网关的请求体，Java 网关收到后会：
 * <ol>
 *   <li>校验参数（message 不能为空）</li>
 *   <li>转换为 PythonChatRequest 格式</li>
 *   <li>转发给 Python AI 引擎进行推理</li>
 *   <li>通过 SSE 将结果流式返回给前端</li>
 * </ol>
 *
 * <p>sessionId 的作用：
 *   sessionId 由前端维护（通常首次对话时生成 UUID），
 *   同一会话下的多轮对话共享同一个 sessionId。
 *   sessionId 为空时，Java 侧会自动生成新的。
 *
 * <p>contextType 决定了 AI 的回答策略：
 * <ul>
 *   <li>general：通用问答模式，不限定领域</li>
 *   <li>investment：投资分析模式，聚焦金融领域</li>
 *   <li>trade：交易辅助模式，提供操作建议</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@Data
public class AiStreamChatRequest {

    /**
     * 用户输入的消息内容。
     * 不能为空，否则请求直接拒绝（HTTP 400）。
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /**
     * 会话 ID（前端维护）。
     * 同一会话下的多轮对话共享此 ID。
     * 为空时 Java 网关会自动生成新的 UUID。
     */
    private String sessionId;

    /**
     * 上下文类型，决定 AI 回答策略。
     * 默认值为 "general"（通用模式）。
     */
    private String contextType = "general";

    /**
     * 上下文引用，标识具体的分析对象。
     * 如股票代码 "600519"、板块名称 "半导体" 等。
     */
    private String contextRef;
}

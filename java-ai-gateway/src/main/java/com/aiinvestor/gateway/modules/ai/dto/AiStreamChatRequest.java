package com.aiinvestor.gateway.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标准化 AI 流式聊天请求。
 */
@Data
public class AiStreamChatRequest {

    /** 用户问题。 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 会话 ID。 */
    private String sessionId;

    /** 上下文类型。 */
    private String contextType = "general";

    /** 上下文引用。 */
    private String contextRef;
}

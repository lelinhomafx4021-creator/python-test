package com.aiinvestor.gateway.modules.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标准化 AI 同步响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseVO {

    /** 会话 ID。 */
    private String sessionId;

    /** 上下文类型。 */
    private String contextType;

    /** 上下文引用。 */
    private String contextRef;

    /** 最终答案。 */
    private String answer;
}

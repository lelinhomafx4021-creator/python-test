package com.aiinvestor.gateway.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会话摘要展示对象
 * 规范：vo 专门用于前端页面展示
 */
@Data
public class ChatSessionSummaryVO {
    private String sessionId;
    private String title;
    private LocalDateTime lastChatTime;
}

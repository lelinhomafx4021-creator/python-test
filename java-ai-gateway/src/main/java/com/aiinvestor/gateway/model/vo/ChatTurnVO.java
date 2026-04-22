package com.aiinvestor.gateway.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天回合展示对象
 */
@Data
public class ChatTurnVO {
    private String query;
    private String answer;
    private String traceId;
    private LocalDateTime createdAt;
}

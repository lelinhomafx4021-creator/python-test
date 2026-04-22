package com.aiinvestor.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 前端 -> Java 网关请求体。
 *
 * 这里不暴露 threadId 给前端，
 * 由 Java 根据 userId + sessionId 统一生成，避免前端乱传。
 */
@Data
public class AiChatRequest {

    @NotBlank
    private String message;

    /**
     * 业务会话ID（由 Java 业务侧生成/管理）。
     */
    @NotBlank
    private String sessionId;
}

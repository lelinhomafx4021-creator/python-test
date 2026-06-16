package com.aiinvestor.gateway.modules.shared.util;

import java.util.UUID;

/**
 * AI 模块通用工具方法
 */
public final class AiUtils {

    private AiUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 归一化会话 ID
     * <p>
     * 前端可能传 null、"null"、"undefined" 等脏值，统一归一化。
     * 如果是脏值或空值，自动生成新的会话 ID。
     *
     * @param sessionId 原始会话 ID
     * @return 归一化后的会话 ID
     */
    public static String normalizeSessionId(String sessionId) {
        String normalized = sessionId == null ? "" : sessionId.trim();
        if (normalized.isEmpty()
                || "null".equalsIgnoreCase(normalized)
                || "undefined".equalsIgnoreCase(normalized)) {
            // 生成新会话 ID（去掉连字符，方便阅读和 URL 传输）
            normalized = "sess_" + UUID.randomUUID().toString().replace("-", "");
        }
        return normalized;
    }
}

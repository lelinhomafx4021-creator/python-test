package com.aiinvestor.gateway.modules.ai.mq;

import java.time.Instant;

/**
 * ============================================================
 * AI 聊天审计事件 - 消息队列中传递的数据载体
 * ============================================================
 *
 * 使用 Java 14+ 的 Record 类型（不可变数据类）：
 *   - 自动生成构造函数、getter、equals、hashCode、toString
 *   - 所有字段 final，天然线程安全
 *   - 非常适合作为 DTO/消息体
 *
 * 与 AiChatAuditDO 的区别：
 *   Event 是消息队列中传输的数据，包含瞬时信息（createdAt 用 Instant）；
 *   DO 是数据库实体，使用 LocalDateTime。
 *
 * @param traceId   追踪 ID
 * @param userId    用户 ID
 * @param sessionId 会话 ID
 * @param endpoint  接口路径
 * @param message   用户消息内容
 * @param createdAt 事件发生时间
 * @author AI Investor Team
 */
public record AiChatAuditEvent(
        String traceId,
        Long userId,
        String sessionId,
        String endpoint,
        String message,
        Instant createdAt
) {
}

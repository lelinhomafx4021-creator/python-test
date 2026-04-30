package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ============================================================
 * AI 聊天审计流水实体 (DO)
 * ============================================================
 *
 * 对应数据库表 ai_chat_audits。
 *
 * 业务意义：
 *   每次用户使用 AI 聊天功能时，异步写入一条审计记录。
 *   可用于：
 *   1. 合规审计：谁在什么时候问了什么
 *   2. 用量统计：每天/每周的调用次数
 *   3. 问题追溯：当用户反馈回答质量问题时回溯上下文
 *
 * @Builder 注解说明：
 *   使用建造者模式构建对象，代码更优雅：
 *   AiChatAuditDO.builder().traceId("xxx").userId("1").build()
 *
 * @author AI Investor Team
 */
@Data
@Builder                                          // 建造者模式
@TableName("ai_chat_audits")
public class AiChatAuditDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全链路追踪 ID */
    private String traceId;

    /** 用户 ID */
    private String userId;

    /** 会话 ID */
    private String sessionId;

    /** 调用的 API 接口路径（如 "/gateway/ai/chat/stream"） */
    private String endpoint;

    /** 用户输入的消息内容（可能截断） */
    private String message;

    /** 审计记录创建时间 */
    private LocalDateTime createdAt;
}

package com.aiinvestor.gateway.modules.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话主表实体。
 *
 * <p>对应数据库表 ai_sessions，记录每次用户与 AI 的对话会话。
 * 一个用户可以拥有多个会话，每个会话下有多个对话轮次（ChatTurnDO）。
 *
 * <p>与 ChatTurnDO 的关系：
 * <ul>
 *   <li>ai_sessions 记录会话级别的元信息（标题、状态、上下文类型）</li>
 *   <li>ai_chat_turns 记录每次具体的问答详情</li>
 *   <li>两者通过 (user_id + session_id) 关联</li>
 * </ul>
 *
 * <p>上下文类型（contextType）用于区分不同业务场景的 AI 会话：
 * <ul>
 *   <li>general：通用问答</li>
 *   <li>investment：投资分析</li>
 *   <li>trade：交易辅助</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@Data
@TableName("ai_sessions")
public class AiSessionDO {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，关联 users 表 */
    private Long userId;

    /** 会话 ID（前端生成，同一会话下所有轮次共享） */
    private String sessionId;

    /** 上下文类型（general / investment / trade），决定 AI 回答策略 */
    private String contextType;

    /** 上下文引用（如股票代码、板块名称等具体分析对象） */
    private String contextRef;

    /** 会话标题（首次对话后由 AI 异步生成，如"茅台2024年财报分析"） */
    private String title;

    /** 会话状态（active 活跃 / archived 归档） */
    private String status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}

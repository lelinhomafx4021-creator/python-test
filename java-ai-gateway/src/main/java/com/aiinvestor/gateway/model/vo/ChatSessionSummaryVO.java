package com.aiinvestor.gateway.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ============================================================
 * 会话摘要展示对象 - 用于左侧会话列表
 * ============================================================
 *
 * 每条记录代表一个"会话"（session），
 * 包含会话的标题和最后活跃时间，
 * 前端用它在侧边栏展示历史会话入口。
 *
 * @author AI Investor Team
 */
@Data
public class ChatSessionSummaryVO {
    /** 会话 ID（前端用此 ID 查询该会话的具体聊天记录） */
    private String sessionId;
    /** AI 自动生成的会话标题（如"茅台2024年财报分析"） */
    private String title;
    /** 最后一次聊天的发生时间（用于排序） */
    private LocalDateTime lastChatTime;
}

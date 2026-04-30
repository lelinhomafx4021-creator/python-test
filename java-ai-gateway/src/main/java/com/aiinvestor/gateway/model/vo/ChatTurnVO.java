package com.aiinvestor.gateway.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ============================================================
 * 聊天回合展示对象
 * ============================================================
 *
 * 一个"回合" = 一次用户提问 + AI 回答。
 * 前端用此对象渲染聊天界面的气泡列表。
 *
 * @author AI Investor Team
 */
@Data
public class ChatTurnVO {
    /** 用户提问的内容 */
    private String query;
    /** AI 的回答（Markdown 格式） */
    private String answer;
    /** 此轮对话的追踪 ID（可用于跳转到日志详情） */
    private String traceId;
    /** 此轮对话的创建时间 */
    private LocalDateTime createdAt;
}

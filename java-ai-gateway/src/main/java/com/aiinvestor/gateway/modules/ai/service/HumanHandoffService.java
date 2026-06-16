package com.aiinvestor.gateway.modules.ai.service;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiHandoffTicketDO;
import com.aiinvestor.gateway.modules.ai.dao.mapper.AiHandoffTicketMapper;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 人工兜底工单服务。
 *
 * <p>负责 AI 转人工场景的核心业务逻辑：
 * <ul>
 *   <li><b>幂等建单</b>：基于 traceId 保证同一请求不会被重复创建工单</li>
 *   <li><b>工单查询</b>：按用户 ID 查询历史工单列表</li>
 * </ul>
 *
 * <p>触发方式：
 * <ol>
 *   <li>用户主动触发：在 AI 对话中输入"转人工"等关键词</li>
 *   <li>系统自动触发：AI 回答质量低于阈值时自动升级为人工处理</li>
 * </ol>
 *
 * <p>工单状态流转：open（待处理） → processing（处理中） → resolved（已解决）/ closed（已关闭）
 *
 * @author AI Investor Team
 */
@Service
public class HumanHandoffService {

    private final AiHandoffTicketMapper handoffTicketMapper;

    /**
     * 构造器注入。
     *
     * @param handoffTicketMapper 工单表 Mapper
     */
    public HumanHandoffService(AiHandoffTicketMapper handoffTicketMapper) {
        this.handoffTicketMapper = handoffTicketMapper;
    }

    /**
     * 幂等创建人工兜底工单。
     *
     * <p>幂等性保证（两个检查点）：
     * <ol>
     *   <li>traceId 为空或空白：直接返回，不做任何操作（防御性编程）</li>
     *   <li>数据库查重：按 traceId 查询是否已存在工单，存在则跳过</li>
     * </ol>
     *
     * <p>建单时状态设为 "open"，等待管理员在工单面板中认领处理。
     *
     * @param traceId        请求追踪 ID（幂等判断依据）
     * @param userId         用户 ID
     * @param sessionId      会话 ID
     * @param threadId       LangGraph 线程 ID
     * @param query          用户原始提问内容
     * @param handoffReason  转人工原因（如"用户要求转人工"、"AI 回答置信度不足"）
     * @param handoffSummary AI 对话的上下文摘要（帮助人工客服快速了解情况）
     */
    public void createTicketIfAbsent(
            String traceId,
            Long userId,
            String sessionId,
            String threadId,
            String query,
            String handoffReason,
            String handoffSummary
    ) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }

        if (handoffTicketMapper.countByTraceId(traceId) > 0) {
            return;
        }

        AiHandoffTicketDO ticket = new AiHandoffTicketDO();
        ticket.setTraceId(traceId);
        ticket.setUserId(userId);
        ticket.setSessionId(sessionId);
        ticket.setThreadId(threadId);
        ticket.setQuery(query);
        ticket.setHandoffReason(handoffReason);
        ticket.setHandoffSummary(handoffSummary);
        ticket.setStatus("open");

        handoffTicketMapper.insert(ticket);
    }

    /**
     * 查询当前用户的人工兜底工单列表。
     *
     * <p>返回结果按建单时间倒序排列，最新的工单在最前面。
     *
     * @param userId 用户 ID
     * @return 该用户的所有工单列表（按 created_at 降序）
     */
    public List<HandoffTicketVO> listTicketsByUserId(Long userId) {
        return handoffTicketMapper.listByUserId(userId);
    }
}

package com.aiinvestor.gateway.modules.ai.service;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiHandoffTicketDO;
import com.aiinvestor.gateway.modules.ai.dao.mapper.AiHandoffTicketMapper;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 人工兜底工单服务。
 * 负责幂等建单与工单列表查询。
 */
@Service
public class HumanHandoffService {

    private final AiHandoffTicketMapper handoffTicketMapper;

    public HumanHandoffService(AiHandoffTicketMapper handoffTicketMapper) {
        this.handoffTicketMapper = handoffTicketMapper;
    }

    /** 当同一 traceId 尚未建单时，创建一张人工兜底工单。 */
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

    /** 查询当前用户的人工兜底工单列表。 */
    public List<HandoffTicketVO> listTicketsByUserId(Long userId) {
        return handoffTicketMapper.listByUserId(userId);
    }
}

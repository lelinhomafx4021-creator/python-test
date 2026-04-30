package com.aiinvestor.gateway.modules.ai.service;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiSessionDO;
import com.aiinvestor.gateway.modules.ai.dao.entity.AiUsageRecordDO;
import com.aiinvestor.gateway.modules.ai.dao.mapper.AiSessionMapper;
import com.aiinvestor.gateway.modules.ai.dao.mapper.AiUsageRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 会话与使用记录服务。
 */
@Service
public class AiSessionService {

    private final AiSessionMapper aiSessionMapper;
    private final AiUsageRecordMapper aiUsageRecordMapper;

    public AiSessionService(AiSessionMapper aiSessionMapper, AiUsageRecordMapper aiUsageRecordMapper) {
        this.aiSessionMapper = aiSessionMapper;
        this.aiUsageRecordMapper = aiUsageRecordMapper;
    }

    /**
     * 触达会话主表。
     */
    @Transactional
    public void touchSession(Long userId, String sessionId, String contextType, String contextRef, String title) {
        AiSessionDO session = aiSessionMapper.selectOne(
                new LambdaQueryWrapper<AiSessionDO>()
                        .eq(AiSessionDO::getUserId, userId)
                        .eq(AiSessionDO::getSessionId, sessionId)
                        .last("limit 1")
        );
        if (session == null) {
            AiSessionDO created = new AiSessionDO();
            created.setUserId(userId);
            created.setSessionId(sessionId);
            created.setContextType(contextType);
            created.setContextRef(contextRef);
            created.setTitle(title);
            created.setStatus("active");
            aiSessionMapper.insert(created);
            return;
        }
        session.setContextType(contextType);
        session.setContextRef(contextRef);
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(title);
        }
        aiSessionMapper.updateById(session);
    }

    /**
     * 记录一次 AI 使用。
     */
    public void recordUsage(Long userId, String featureCode, String membershipLevel, String traceId, String status) {
        AiUsageRecordDO record = new AiUsageRecordDO();
        record.setUserId(userId);
        record.setFeatureCode(featureCode);
        record.setMembershipLevel(membershipLevel);
        record.setTraceId(traceId);
        record.setRequestTokens(0);
        record.setResponseTokens(0);
        record.setStatus(status);
        record.setCreatedAt(LocalDateTime.now());
        aiUsageRecordMapper.insert(record);
    }
}

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
 *
 * <p>负责两个核心职责：
 * <ol>
 *   <li><b>会话管理</b>：首次对话时自动创建会话，后续对话更新上下文信息</li>
 *   <li><b>用量记录</b>：每次 AI 调用完成后记录一条消耗流水</li>
 * </ol>
 *
 * <p>touchSession 的"触碰"语义：
 *   每次用户发送消息时"触碰"会话记录 —— 如果该 (userId, sessionId) 组合不存在则创建，
 *   存在则更新 contextType 和 contextRef（恰好反映用户最新关注的话题）。
 *
 * @author AI Investor Team
 */
@Service
public class AiSessionService {

    private final AiSessionMapper aiSessionMapper;
    private final AiUsageRecordMapper aiUsageRecordMapper;

    /**
     * 构造器注入（Spring 推荐方式，无需 @Autowired）。
     *
     * @param aiSessionMapper     会话表 Mapper
     * @param aiUsageRecordMapper 用量记录表 Mapper
     */
    public AiSessionService(AiSessionMapper aiSessionMapper, AiUsageRecordMapper aiUsageRecordMapper) {
        this.aiSessionMapper = aiSessionMapper;
        this.aiUsageRecordMapper = aiUsageRecordMapper;
    }

    /**
     * 触达会话主表 —— 存在则更新上下文，不存在则新建。
     *
     * <p>业务逻辑：
     * <ol>
     *   <li>按 (userId, sessionId) 查询是否存在已有会话</li>
     *   <li>不存在：插入新会话记录，状态设为 active，标题使用传入值（可能是默认标题）</li>
     *   <li>存在：更新 contextType 和 contextRef；如果原标题为空才覆盖（AI 生成的标题不应被覆盖）</li>
     * </ol>
     *
     * <p>事务保证：@Transactional 确保插入/更新操作的原子性。
     *
     * @param userId      用户 ID
     * @param sessionId   会话 ID（前端生成）
     * @param contextType 上下文类型（general / investment / trade）
     * @param contextRef  上下文引用（股票代码、板块名等）
     * @param title       会话标题（首次对话时可能是默认值，后续由 AI 异步更新）
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
            // 首次对话：创建新会话记录
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
        // 已有会话：更新上下文信息
        session.setContextType(contextType);
        session.setContextRef(contextRef);
        // 仅当原标题为空时才覆盖（保护 AI 异步生成的标题不被默认标题覆盖）
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(title);
        }
        aiSessionMapper.updateById(session);
    }

    /**
     * 记录一次 AI 使用（异步写入用量流水）。
     *
     * <p>每次 AI 调用完成后调用此方法，插入一条用量记录。
     * Token 数字初始为 0，后续由异步回调或定时任务回填真实数据。
     *
     * <p>该方法不阻塞主流程，可考虑通过消息队列异步执行以降低延迟。
     *
     * @param userId          用户 ID
     * @param featureCode     功能编码（如 ai_chat_stream / ai_title_gen）
     * @param membershipLevel 会员等级（free / vip / svip）
     * @param traceId         全链路追踪 ID
     * @param status          调用状态（success / failed / partial）
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

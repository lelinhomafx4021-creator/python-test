package com.aiinvestor.gateway.service;

import com.aiinvestor.gateway.dao.entity.ChatTurnDO;
import com.aiinvestor.gateway.dao.mapper.ChatTurnMapper;
import com.aiinvestor.gateway.model.vo.ChatSessionSummaryVO;
import com.aiinvestor.gateway.model.vo.ChatTurnVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * -----------------------------------------------------------
 * 【档案室：ChatHistoryService】
 * -----------------------------------------------------------
 * 这个类负责所有聊天历史的“存、取、改”。
 * 
 * 知识点：
 * 1. 异步副作用处理 (Async Side Effects)
 * 2. 数据库逻辑下沉：性能优化的关键在于让数据库多干活，Java 少干活。
 */
@Service
public class ChatHistoryService {

    private final ChatTurnMapper chatTurnMapper;
    private final PythonAiClientService pythonAiClientService;

    public ChatHistoryService(ChatTurnMapper chatTurnMapper, PythonAiClientService pythonAiClientService) {
        this.chatTurnMapper = chatTurnMapper;
        this.pythonAiClientService = pythonAiClientService;
    }

    /**
     * 【黑科技逻辑】保存对话并自动生成精美标题
     * 
     * 这里体现了“异步非阻塞”思想。拟定一个漂亮的标题需要请大模型帮忙，
     * 动作很慢，用户肯定等不及。所以我们存完数据立刻返回，让他在后台慢慢跑。
     */
    public void saveTurn(
            String userId, String sessionId, String threadId, String traceId,
            String query, String answer, String intent, String source,
            boolean reviewPassed, String responseMode, int a2aCount
    ) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        // --- 1. 数据建模入库 ---
        ChatTurnDO row = new ChatTurnDO();
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setThreadId(threadId);
        row.setTraceId(traceId);
        row.setQuery(query);
        row.setAnswer(answer);
        row.setIntent(intent);
        row.setSource(source);
        row.setReviewPassed(reviewPassed);
        row.setResponseMode(responseMode);
        row.setA2aCount(a2aCount);
        
        // 兜底标题：先随便给一个前10个字，避免侧边栏空着
        row.setTitle(query.length() > 10 ? query.substring(0, 10) : query);
        
        // MyBatis-Plus 自动执行插入 SQL
        chatTurnMapper.insert(row);

        // --- 2. 异步处理副作用 ---
        // 面试讲点：这是典型的后端优化。主流程只需 10ms，异步流程可能需要 1000ms。
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // 只有当这是这个会话的第一轮时，才去请 AI 拟定标题（节省 token，节省性能）
                if (chatTurnMapper.countBySession(userId, sessionId) <= 1) {
                    String aiTitle = pythonAiClientService.generateTitle(query);
                    // 异步更新数据库里的标题字段
                    chatTurnMapper.updateTitle(userId, sessionId, aiTitle);
                }
            } catch (Exception e) {
                // 异步任务的报错严禁向上抛，不能影响到用户的聊天。记录日志即可。
            }
        });
    }

    public void updateTurnAnswerByTraceId(String traceId, String answer) {
        if (traceId == null || traceId.trim().isEmpty()) {
            return;
        }
        chatTurnMapper.updateAnswerByTraceId(traceId, answer == null ? "" : answer);
    }

    /**
     * 查询对话回合（支持真分页）
     */
    public List<ChatTurnVO> listTurns(String userId, String sessionId, int limit, int offset) {
        // 面试讲点：防御式编程。千万不要相信前端传来的参数，手动做边界校验。
        int finalLimit = Math.max(1, Math.min(200, limit)); // 限制范围 1-200，防止大分页内存溢出
        int finalOffset = Math.max(0, offset);

        return chatTurnMapper.listTurnDetails(userId, sessionId, finalLimit, finalOffset);
    }

    /**
     * 查询侧边栏会话列表
     */
    public List<ChatSessionSummaryVO> listSessions(String userId) {
        // 这里调用的 Mapper 方法内部使用了 GROUP BY。
        // 规范：能用一条 SQL 实现的聚合，千万不要在 Java 代码里跑循环，那是低级错误。
        return chatTurnMapper.listSessionSummaries(userId);
    }
}

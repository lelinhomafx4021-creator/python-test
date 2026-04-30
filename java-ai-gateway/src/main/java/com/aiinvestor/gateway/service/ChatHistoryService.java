package com.aiinvestor.gateway.service;

import com.aiinvestor.gateway.dao.entity.ChatTurnDO;
import com.aiinvestor.gateway.dao.mapper.ChatTurnMapper;
import com.aiinvestor.gateway.model.vo.ChatSessionSummaryVO;
import com.aiinvestor.gateway.model.vo.ChatTurnVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ============================================================
 * 聊天历史服务 - 聊天记录的"档案室"
 * ============================================================
 *
 * 职责：
 *   所有聊天历史的存入、查询、更新操作都通过此 Service。
 *
 * 核心设计理念：
 *   1. 异步副作用：耗时操作（AI 生成标题）不阻塞主流程
 *   2. 数据库逻辑下沉：能一条 SQL 完成的事，绝不在 Java 里循环
 *   3. 防御式编程：不信任前端传来的参数，做边界校验
 *
 * @author AI Investor Team
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
     * 保存一轮对话，并异步生成会话标题。
     *
     * 这是本项目中"异步非阻塞"思想的最佳范例：
     *
     * 用户点击发送 → 立刻返回"思考中..."让用户看到反馈
     *   ├── [同步 10ms] 插入占位记录到数据库（answer = "思考中..."）
     *   ├── [异步 1000ms] 调用 Python AI 生成精美标题 → 回填数据库
     *   └── [流式] Python 逐 token 返回答案 → SSE 推给前端
     *
     * 关键：标题生成花了 1000ms，但用户完全感知不到！
     *       因为它在 CompletableFuture 的后台线程中执行。
     *
     * @param userId       用户 ID
     * @param sessionId    会话 ID
     * @param threadId     LangGraph 线程 ID
     * @param traceId      追踪 ID
     * @param query        用户提问内容
     * @param answer       初始回答（通常为"[思考中...]"）
     * @param intent       意图类型（如 "investment"）
     * @param source       数据来源标识
     * @param reviewPassed 是否通过幻觉检测
     * @param responseMode 响应模式（如 "stream"）
     * @param a2aCount     Agent-to-Agent 对话轮数
     */
    public void saveTurn(
            String userId, String sessionId, String threadId, String traceId,
            String query, String answer, String intent, String source,
            boolean reviewPassed, String responseMode, int a2aCount
    ) {
        // 参数防御：sessionId 不能为空
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        // ---------- 步骤 1：数据建模入库 ----------
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

        // 兜底标题：取用户提问的前 10 个字符，避免侧边栏空着
        row.setTitle(query.length() > 10 ? query.substring(0, 10) : query);

        // MyBatis-Plus 自动执行 INSERT SQL
        chatTurnMapper.insert(row);

        // ---------- 步骤 2：异步处理副作用 ----------
        // 面试重点：主流程 10ms 完成，异步流程 1000ms 不阻塞用户
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // 只有该会话的第一轮对话才生成标题
                // 标题生成贵（要调 LLM），后面的轮次没必要浪费 token
                if (chatTurnMapper.countBySession(userId, sessionId) <= 1) {
                    // 调用 Python 大模型生成标题
                    String aiTitle = pythonAiClientService.generateTitle(query);
                    // 异步回填标题到数据库
                    chatTurnMapper.updateTitle(userId, sessionId, aiTitle);
                }
            } catch (Exception e) {
                // 异步任务的异常绝不能向上抛
                // 原因：CompletableFuture 中未捕获的异常只会被静默吞掉
                //       或导致未处理的 Future 异常。这里记录日志即可。
            }
        });
    }

    /**
     * 根据 traceId 回填 AI 的最终回答。
     *
     * 调用时机：
     *   Python 流式返回阶段为 "final_answer" 时触发。
     *   此时 AI 已经回答完毕，用真正的答案替换数据库中的"[思考中...]"。
     *
     * @param traceId 追踪 ID（唯一标识一次请求）
     * @param answer  AI 的最终回答文本（Markdown 格式）
     */
    public void updateTurnAnswerByTraceId(String traceId, String answer) {
        if (traceId == null || traceId.trim().isEmpty()) {
            return; // 防御：空 traceId 不执行
        }
        chatTurnMapper.updateAnswerByTraceId(traceId, answer == null ? "" : answer);
    }

    /**
     * 分页查询某个会话下的聊天详情。
     *
     * 防御式编程：
     *   limit 限制范围 [1, 200]，防止：
     *   - limit=0 导致啥也查不到
     *   - limit=100000 导致内存溢出
     *   offset 限制 >= 0，防止负数导致 SQL 语法错误
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param limit     每页条数
     * @param offset    偏移量
     * @return 聊天详情列表（按时间正序）
     */
    public List<ChatTurnVO> listTurns(String userId, String sessionId, int limit, int offset) {
        // Math.max(1, Math.min(200, limit)) → 将 limit 钳制在 [1, 200]
        int finalLimit = Math.max(1, Math.min(200, limit));
        // offset 不能为负数
        int finalOffset = Math.max(0, offset);

        return chatTurnMapper.listTurnDetails(userId, sessionId, finalLimit, finalOffset);
    }

    /**
     * 查询用户的会话列表（左侧边栏）。
     *
     * 性能要点：
     *   Mapper 中的 SQL 用了 GROUP BY + MAX + COUNT + ORDER BY，
     *   所有聚合逻辑在数据库完成，Java 拿到的就是最终结果。
     *
     *   如果在 Java 代码里做 GROUP BY（查全部记录 → 循环分组 → 排序），
     *   数据量大时会非常慢 —— 这是初级程序员的常见错误。
     *
     * @param userId 用户 ID
     * @return 会话列表（按最后活跃时间降序）
     */
    public List<ChatSessionSummaryVO> listSessions(String userId) {
        return chatTurnMapper.listSessionSummaries(userId);
    }
}

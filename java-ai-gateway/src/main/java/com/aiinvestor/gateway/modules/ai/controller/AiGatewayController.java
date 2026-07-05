package com.aiinvestor.gateway.modules.ai.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.util.AiUtils;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.ai.vo.ChatSessionSummaryVO;
import com.aiinvestor.gateway.modules.ai.vo.ChatTurnVO;
import com.aiinvestor.gateway.modules.ai.service.ChatHistoryService;
import com.aiinvestor.gateway.modules.ai.service.HumanHandoffService;
import com.aiinvestor.gateway.modules.ai.service.PythonAiClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================
 * AI 网关控制器 - 处理所有 AI 聊天相关的 HTTP 请求
 * ============================================================
 *
 * 这个 Controller 是整个 Java 网关最核心的入口，
 * 负责：
 *   1. 鉴权（通过 @LoginRequired 和 LoginInterceptor）
 *   2. 生成 traceId / threadId（全链路追踪）
 *   3. 会话管理（session 创建和管理）
 *   4. 聊天历史写入（占位 → 流式回填）
 *   5. SSE 流式透传（Python → Java → 浏览器）
 *
 * 接口列表：
 *   GET /gateway/ai/chat/stream  → 流式聊天（核心接口）
 *   GET /gateway/ai/sessions     → 会话列表（左侧边栏）
 *   GET /gateway/ai/history      → 聊天记录（分页）
 *
 * @author AI Investor Team
 */
@Slf4j
@RequiredArgsConstructor
@Validated                                         // 启用参数校验
@RestController                                    // REST 控制器
@RequestMapping("/gateway/ai")                     // 接口前缀
@LoginRequired                                     // 整个 Controller 下的接口都需要登录
@Tag(name = "AI对话", description = "AI 智能对话、会话管理、聊天历史查询")
public class AiGatewayController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Python AI 通信服务 */
    private final PythonAiClientService pythonAiClientService;

    /** 聊天历史管理服务 */
    private final ChatHistoryService chatHistoryService;

    /** 人工兜底工单服务 */
    private final HumanHandoffService humanHandoffService;

    /**
     * =====================================================
     * 【核心接口】流式 AI 聊天
     * =====================================================
     *
     * 一整次请求的完整生命周期：
     *
     *   ① 浏览器发起 GET /gateway/ai/chat/stream?message=茅台怎么样&sessionId=xxx
     *   ② Java 网关：
     *      a. 生成 traceId（UUID）
     *      b. 拼接 threadId = userId:sessionId
     *      c. 写入占位记录（answer = "[思考中...]"）
     *      d. 转发请求到 Python SSE 接口
     *   ③ Python AI 引擎：
     *      a. 接收请求 → LangGraph 多 Agent 协作 → 逐 token 生成
     *      b. 每个 token 通过 SSE 发回 Java
     *   ④ Java 网关：
     *      a. 收到 SSE 事件 → 立刻转发给浏览器（不缓存）
     *      b. 收到 final_answer → 提取最终回答 → 回填数据库
     *   ⑤ 浏览器：
     *      前端通过 EventSource API 逐 token 渲染 AI 回答
     *
     * 关键设计决策：
     *   - 为什么不是先等 Python 完整回答再返回？
     *     因为大模型生成一个回答可能要 10-30 秒，
     *     用户盯着空白页面会流失。
     *     SSE 流式返回让用户实时看到文字生成过程，体验好比打字机。
     *
     *   - 为什么先写"[思考中...]"再回填？
     *     因为 SSE 流式推送过程中，前端已经从 EventSource 拿到了数据，
     *     但数据库需要持久化。如果在流结束前不写占位记录，
     *     用户刷新页面时可能看不到这条对话。
     *
     * @param message   用户输入的消息（必填，不能为空）
     * @param sessionId 会话 ID（可选，为空时自动生成新会话）
     * @return SSE 事件流（content-type: text/event-stream）
     */
    /** 【核心接口】流式 AI 聊天，返回 SSE 事件流 */
    @Operation(summary = "流式AI聊天", description = "向AI投研助手发送消息，通过SSE流式返回回答内容")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @Parameter(description = "用户输入的消息（必填）", required = true, example = "茅台怎么样")
            @RequestParam("message") @NotBlank(message = "消息内容不能为空") String message,
            @Parameter(description = "会话ID（可选，为空时自动生成新会话）", required = false)
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        // ---------- 步骤 1：处理 sessionId ----------
        final String finalSessionId = AiUtils.normalizeSessionId(sessionId);

        // ---------- 步骤 2：获取当前用户 ----------
        // 此时已通过 @LoginRequired → LoginInterceptor 的校验，
        // 用户对象已在 UserContext 中。
        Long userId = UserContext.getUserId();

        // ---------- 步骤 3：生成追踪和线程 ID ----------
        // traceId  = 本次请求唯一标识（用于日志关联）
        // threadId = 会话线程标识（用于 LangGraph 的 checkpointer 持久化对话上下文）
        final String traceId = UUID.randomUUID().toString();
        String threadId = pythonAiClientService.buildThreadId(userId, finalSessionId);
        log.info("AI stream start traceId={}, userId={}, sessionId={}", traceId, userId, finalSessionId);

        // ---------- 步骤 4：写入占位记录 ----------
        // answer 先写 "[思考中...]"，等 Python 返回 final_answer 时再回填
        chatHistoryService.saveTurn(
                userId,
                finalSessionId,
                threadId,
                traceId,
                message,
                "[思考中...]",      // 占位文本
                "investment",        // 默认意图为投资分析
                "python_stream",     // 来源标识
                true,                // reviewPassed 默认 true（Python 端会重新检测）
                "stream",            // 流式响应模式
                0                    // A2A 轮数初始为 0
        );

        // ---------- 步骤 5：透传 SSE 事件流 ----------
        // 从 UserContext 获取用户角色，传递给 Python 端决定使用哪套图流程
        String userRole = UserContext.get() != null ? UserContext.get().getRole() : "normal";
        return pythonAiClientService.streamChatSse(message, userId, finalSessionId, userRole, traceId)
                // 对每个 SSE 事件做副处理
                .doOnNext(sse -> {
                    try {
                        String raw = sse.data();
                        // 跳过空数据
                        if (raw == null || raw.isBlank()) {
                            return;
                        }
                        // 解析 JSON，获取阶段标识
                        JsonNode node = OBJECT_MAPPER.readTree(raw);
                        String stage = node.path("stage").asText();

                        // "final_answer" 表示 AI 已完成回答
                        // 此时从 data.answer 中提取完整答案，回填数据库
                        if ("final_answer".equals(stage)) {
                            String answer = node.path("data").path("answer").asText("");
                            chatHistoryService.updateTurnAnswerByTraceId(traceId, answer);
                            log.info("AI stream final_answer traceId={}, answerLength={}", traceId, answer.length());
                        } else if ("handoff".equals(stage)) {
                            JsonNode data = node.path("data");
                            String handoffReason = data.path("reason").asText("");
                            String handoffSummary = data.path("summary").asText("");
                            // [教学修改] handoff 阶段会出现两次：
                            // 1. 图刚进入 handoff 节点时，只带一个 step 提示；
                            // 2. 真正的人工交接事件，才会带 reason/summary。
                            // 所以这里要等关键信息齐了再建单，避免落一个“空原因工单”。
                            if (!handoffReason.isBlank() || !handoffSummary.isBlank()) {
                                log.info("AI stream handoff traceId={}, reason={}", traceId, handoffReason);
                                humanHandoffService.createTicketIfAbsent(
                                        traceId,
                                        userId,
                                        finalSessionId,
                                        threadId,
                                        message,
                                        handoffReason,
                                        handoffSummary
                                );
                            }
                        }
                        // 其他阶段（如 "thinking"、"tool_call"）不做数据库操作
                    } catch (Exception e) {
                        log.debug("SSE 事件解析失败 traceId={}", traceId, e);
                    }
                })
                .doFinally(signalType -> log.info("AI stream finished traceId={}, signal={}", traceId, signalType));
    }

    /** 获取当前用户的会话列表，用于前端左侧边栏展示历史会话入口 */
    @Operation(summary = "获取会话列表", description = "获取当前登录用户的所有AI对话会话列表（按最后活跃时间降序）")
    @GetMapping("/sessions")
    public ApiResult<List<ChatSessionSummaryVO>> sessions() {
        Long userId = UserContext.getUserId();
        return ApiResult.ok(chatHistoryService.listSessions(userId));
    }

    /** 获取某个会话下的聊天历史，支持分页 */
    @Operation(summary = "获取聊天历史", description = "获取指定会话的聊天记录，支持分页（limit 1-200，默认20）")
    @GetMapping("/history")
    public ApiResult<List<ChatTurnVO>> history(
            @Parameter(description = "会话ID（必填）", required = true, example = "sess_abc123")
            @RequestParam("session_id") @NotBlank(message = "会话ID不能为空") String sessionId,
            @Parameter(description = "每页条数（1-200，默认20）", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
            @Parameter(description = "偏移量（≥0，默认0）", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        Long userId = UserContext.getUserId();
        return ApiResult.ok(chatHistoryService.listTurns(userId, sessionId, limit, offset));
    }
    /** 查询当前登录用户的人工兜底工单列表 */
    @Operation(summary = "查询人工工单", description = "获取当前用户的人工兜底工单列表（AI转人工闭环记录）")
    @GetMapping("/handoff-tickets")
    public ApiResult<List<HandoffTicketVO>> handoffTickets() {
        Long userId = UserContext.getUserId();
        return ApiResult.ok(humanHandoffService.listTicketsByUserId(userId));
    }
}

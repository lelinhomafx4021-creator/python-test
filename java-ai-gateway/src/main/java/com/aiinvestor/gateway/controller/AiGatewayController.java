package com.aiinvestor.gateway.controller;

import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.model.vo.ChatSessionSummaryVO;
import com.aiinvestor.gateway.model.vo.ChatTurnVO;
import com.aiinvestor.gateway.service.ChatHistoryService;
import com.aiinvestor.gateway.service.PythonAiClientService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * -----------------------------------------------------------
 * 【大学生面试项目：AI 投研系统 - 网关指挥部】
 * -----------------------------------------------------------
 * 这个类是所有 AI 对话的入口。它就像一个指挥官，先看你有没有买票（登录），
 * 再看你的要求合不合法（校验），最后把活儿交给 Python 去做（调度）。
 */
@CrossOrigin // 开启跨域支持，允许前端页面跨端口调用
@Validated     
@RestController
@RequestMapping("/gateway/ai")
public class AiGatewayController {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    // final + 构造函数注入：这是 Spring 官方最推崇的稳健写法，面试必加分
    private final PythonAiClientService pythonAiClientService;
    private final ChatHistoryService chatHistoryService;

    public AiGatewayController(PythonAiClientService pythonAiClientService, 
                               ChatHistoryService chatHistoryService) {
        this.pythonAiClientService = pythonAiClientService;
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * 【黑科技接口】流式聊天 (SSE)
     * 
     * 场景：AI 说话慢，我们要像 ChatGPT 一样一个字一个字吐出来。
     * 技术：MediaType.TEXT_EVENT_STREAM_VALUE 表示这是一个持续不断的长连接流。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam("message") @NotBlank(message = "消息内容不能为空") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "userId", required = false) String userIdFromQuery) {
        String normalized = (sessionId == null) ? "" : sessionId.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            normalized = "sess_" + UUID.randomUUID().toString().replace("-", "");
        }
        final String finalSessionId = normalized;

        // EventSource 默认无法带自定义 Header；允许 query 参数携带 userId 兜底。
        Long userId = UserContext.getUserId();
        String userIdStr;
        if (userId != null) {
            userIdStr = String.valueOf(userId);
        } else if (userIdFromQuery != null && !userIdFromQuery.trim().isEmpty()) {
            userIdStr = userIdFromQuery.trim();
        } else {
            userIdStr = "1";
        }

        // 构建链路追踪 ID，后续用于回填最终答案
        final String traceId = UUID.randomUUID().toString();

        String threadId = pythonAiClientService.buildThreadId(userIdStr, finalSessionId);

        chatHistoryService.saveTurn(
                userIdStr,
                finalSessionId,
                threadId,
                traceId,
                message,
                "[思考中...]",
                "investment",
                "python_stream",
                true,
                "stream",
                0
        );

        return pythonAiClientService.streamChatSse(message, userIdStr, finalSessionId)
                .doOnNext(sse -> {
                    try {
                        String raw = sse.data();
                        if (raw == null || raw.isBlank()) {
                            return;
                        }
                        com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(raw);
                        String stage = node.path("stage").asText();
                        if ("final_answer".equals(stage)) {
                            String answer = node.path("data").path("answer").asText("");
                            chatHistoryService.updateTurnAnswerByTraceId(traceId, answer);
                        }
                    } catch (Exception ignored) {
                    }
                });
    }

    /**
     * 获取用户的所有聊天列表（左侧侧边栏用）
     */
    @GetMapping("/sessions")
    public ApiResult<List<ChatSessionSummaryVO>> sessions() {
        Long userId = UserContext.getUserId();
        String userIdStr = (userId == null) ? "1" : String.valueOf(userId);
        return ApiResult.ok(chatHistoryService.listSessions(userIdStr));
    }

    /**
     * 获取单一会话的全部回合，用于恢复聊天场景
     */
    @GetMapping("/history")
    public ApiResult<List<ChatTurnVO>> history(
            @RequestParam("session_id") @NotBlank(message = "会话ID不能为空") String sessionId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        Long userId = UserContext.getUserId();
        String userIdStr = (userId == null) ? "1" : String.valueOf(userId);
        return ApiResult.ok(chatHistoryService.listTurns(userIdStr, sessionId, limit, offset));
    }
}

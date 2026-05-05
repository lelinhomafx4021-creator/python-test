package com.aiinvestor.gateway.modules.ai.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.ai.controller.AiGatewayController;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.ai.dto.AiStreamChatRequest;
import com.aiinvestor.gateway.modules.ai.service.AiSessionService;
import com.aiinvestor.gateway.modules.ai.vo.AiChatResponseVO;
import com.aiinvestor.gateway.modules.ai.service.HumanHandoffService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * AI 标准化门面控制器。
 * 这层不取代旧网关接口，而是为会员终端一期提供更稳定的 `/api/v1/ai/*` 对外契约。
 */
@RestController
@RequestMapping("/api/v1/ai")
@LoginRequired
@Tag(name = "AI对话(标准API)", description = "AI 标准化对话接口（POST /chat/stream、/chat、/handoff-tickets）")
public class AiPortalController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiGatewayController aiGatewayController;
    private final HumanHandoffService humanHandoffService;
    private final AiSessionService aiSessionService;

    public AiPortalController(AiGatewayController aiGatewayController,
                              HumanHandoffService humanHandoffService,
                              AiSessionService aiSessionService) {
        this.aiGatewayController = aiGatewayController;
        this.humanHandoffService = humanHandoffService;
        this.aiSessionService = aiSessionService;
    }

    /** 标准化流式聊天接口。 */
    @Operation(summary = "标准化流式聊天", description = "发送消息并以 SSE 流式方式接收 AI 回答（标准化 API）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody AiStreamChatRequest request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        String title = request.getMessage().length() > 12 ? request.getMessage().substring(0, 12) : request.getMessage();
        aiSessionService.touchSession(
                UserContext.getUserId(),
                sessionId,
                request.getContextType(),
                request.getContextRef(),
                title
        );

        return aiGatewayController.stream(request.getMessage(), sessionId)
                .doFinally(signalType -> aiSessionService.recordUsage(
                        UserContext.getUserId(),
                        "ai_chat_daily",
                        UserContext.get().getRole(),
                        sessionId,
                        "success"
                ));
    }

    /** 标准化同步聊天接口，基于流式接口聚合最终答案 */
    @Operation(summary = "同步AI对话", description = "发送消息并等待 AI 完整回答后返回（同步模式）")
    @PostMapping("/chat")
    public Mono<ApiResult<AiChatResponseVO>> chat(@Valid @RequestBody AiStreamChatRequest request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        String title = request.getMessage().length() > 12 ? request.getMessage().substring(0, 12) : request.getMessage();
        aiSessionService.touchSession(
                UserContext.getUserId(),
                sessionId,
                request.getContextType(),
                request.getContextRef(),
                title
        );

        return aiGatewayController.stream(request.getMessage(), sessionId)
                .map(ServerSentEvent::data)
                // 流式事件里既有 accepted / intent，也有 final_answer。
                // Reactor 的 map 不允许返回 null，所以这里改成先提取、再按需下发。
                .handle((String raw, SynchronousSink<String> sink) -> {
                    String answer = extractFinalAnswer(raw);
                    if (answer != null && !answer.isBlank()) {
                        sink.next(answer);
                    }
                })
                .next()
                .switchIfEmpty(Mono.just(""))
                .map(answer -> ApiResult.ok(new AiChatResponseVO(
                        sessionId,
                        request.getContextType(),
                        request.getContextRef(),
                        answer
                )));
    }

    /** 标准化人工工单查询接口。 */
    @Operation(summary = "查询人工工单(标准API)", description = "获取当前用户的人工兜底工单列表（标准化 API）")
    @GetMapping("/handoff-tickets")
    public ApiResult<List<HandoffTicketVO>> handoffTickets() {
        return ApiResult.ok(humanHandoffService.listTicketsByUserId(UserContext.getUserId()));
    }

    private String normalizeSessionId(String sessionId) {
        String normalized = sessionId == null ? "" : sessionId.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return "sess_" + UUID.randomUUID().toString().replace("-", "");
        }
        return normalized;
    }

    private String extractFinalAnswer(String raw) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if ("final_answer".equals(node.path("stage").asText())) {
                return node.path("data").path("answer").asText("");
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}

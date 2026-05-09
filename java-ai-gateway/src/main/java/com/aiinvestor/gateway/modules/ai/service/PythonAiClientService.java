package com.aiinvestor.gateway.modules.ai.service;

import com.aiinvestor.gateway.modules.ai.dto.PythonChatRequest;
import com.aiinvestor.gateway.modules.ai.mq.AiChatAuditEvent;
import com.aiinvestor.gateway.modules.ai.mq.AiChatAuditProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================
 * Python AI 客户端服务 - Java 与 Python 的"外交中心"
 * ============================================================
 *
 * 职责：
 *   负责 Java 网关与 Python AI 引擎之间的通信。
 *   所有对 Python 的 HTTP 调用都通过此 Service 进行。
 *
 * 核心技术栈：
 *   1. WebClient（响应式非阻塞 HTTP 客户端）
 *      - 替代已弃用的 RestTemplate
 *      - 基于 Netty，支持 SSE 流式数据
 *      - 线程不阻塞等待响应，性能远超 RestTemplate
 *
 *   2. SSE（Server-Sent Events，服务器推送事件）
 *      - AI 回答是逐 token 生成的，不能等全部生成完再返回
 *      - SSE 允许 Python 一边生成一边推送，Java 一边接收一边转给前端
 *      - 前端浏览器通过 EventSource API 实时消费 SSE 事件流
 *
 *   3. Reactor（Flux/Mono 响应式编程）
 *      - Flux<ServerSentEvent>：0..N 个事件的异步序列
 *      - .doOnNext()：对每个事件做副作用处理（如更新数据库）
 *      - .doFinally()：流结束后执行清理工作（如发送审计消息）
 *
 * @author AI Investor Team
 */
@Slf4j
@Service
public class PythonAiClientService {

    /** 专门调 Python 的 HTTP 客户端（由 WebClientConfig 配置） */
    private final WebClient pythonAiWebClient;

    /** 审计消息生产者（用于异步入库，不阻塞主流程） */
    private final AiChatAuditProducer auditProducer;

    public PythonAiClientService(WebClient pythonAiWebClient, AiChatAuditProducer auditProducer) {
        this.pythonAiWebClient = pythonAiWebClient;
        this.auditProducer = auditProducer;
    }

    /**
     * 构建 Python 端 LangGraph 可识别的统一会话 ID。
     *
     * 格式：userId:sessionId
     * 例如："1:sess_abc123def456"
     *
     * @param userId    用户 ID
     * @param sessionId 前端维护的会话 ID
     * @return 拼接后的 threadId
     */
    public String buildThreadId(Long userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    /**
     * SSE 流式透传 - Java 网关的核心能力。
     *
     * 这是一个"管道（Pipeline）"模式：
     *   Python 逐 token 生成 → Java 逐块接收 → 实时推送给前端浏览器
     *
     * 流程图：
     *   用户浏览器 ← SSE ← Java 网关 ← SSE ← Python AI 引擎
     *
     * @param message   用户输入的消息
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param role      用户角色（"normal" 或 "vip"），决定 Python 端使用哪套图流程
     * @return SSE 事件流（Flux 响应式类型）
     */
    public Flux<ServerSentEvent<String>> streamChatSse(String message, Long userId, String sessionId, String role) {
        // 生成本次请求的追踪 ID
        String traceId = UUID.randomUUID().toString();

        return pythonAiWebClient.post()
                // 目标 URL：Python 服务的 AI 聊天流接口
                .uri("/ai/v1/chat/stream")
                // 告诉 Python：我期望 SSE 格式的响应
                .accept(MediaType.TEXT_EVENT_STREAM)
                // 请求体是 JSON 格式
                .contentType(MediaType.APPLICATION_JSON)
                // 构造请求体：消息内容 + 线程ID + 追踪ID + 用户角色
                .bodyValue(new PythonChatRequest(message, userId + ":" + sessionId, traceId, role))
                // 发起请求并获取响应
                .retrieve()
                // 将响应体解析为 SSE 事件流
                // ParameterizedTypeReference 用于传递泛型类型（Java 泛型擦除的解决方案）
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                // ---- 以下为响应式操作符链 ----
                // 每个事件到达时打印日志（方便调试）
                .doOnNext(event -> {
                    String data = event.data();
                    String preview = data == null ? "null" :
                            (data.length() > 500 ? data.substring(0, 500) + "...(truncated)" : data);
                    log.info("[streamChatSse] python->java event={}, data={}", event.event(), preview);
                })
                // 过滤掉空数据事件（兼容性处理）
                .filter(event -> event.data() != null && !event.data().isBlank())
                // 标准化事件结构：event 字段为空时默认用 "message"
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.event() == null || event.event().isBlank() ? "message" : event.event())
                        .id(event.id())
                        .data(event.data())
                        .build())
                // 流全部结束后，异步发送审计消息（不阻塞流的结束）
                .doFinally(signalType -> {
                    try {
                        auditProducer.send(new AiChatAuditEvent(
                                traceId, userId, sessionId, "/ai/chat/stream", message, Instant.now()
                        ));
                    } catch (Exception e) {
                        // 审计失败不影响主业务，但记录日志便于排查
                        log.warn("[streamChatSse] 审计消息发送失败: {}", e.getMessage());
                    }
                });
    }

    /**
     * 调用 Python LLM 生成会话标题。
     *
     * 与 streamChatSse 的区别：
     *   这是一个普通 HTTP 调用（非流式），用 .block() 同步等待结果。
     *   因为标题生成是在异步线程中执行的（CompletableFuture），
     *   用 .block() 阻塞该异步线程是合理的。
     *
     * @param query 用户的首条提问内容
     * @return AI 生成的标题，若失败则返回 query 的前 10 个字符作为兜底
     */
    public String generateTitle(String query) {
        try {
            // 构造请求体
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("query", query);

            log.info("[generateTitle] calling python util, query={}", query);

            // 同步调用 Python 的标题生成工具接口
            com.fasterxml.jackson.databind.JsonNode response = pythonAiWebClient.post()
                    .uri("/ai/v1/util/generate_title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                    .block(); // 同步等待结果（在异步线程中，阻塞是安全的）

            // 提取返回的标题
            if (response != null && response.has("data")) {
                String title = response.get("data").get("title").asText();
                log.info("[generateTitle] python returned title={}", title);
                return title;
            }

            log.warn("[generateTitle] python returned empty data, fallback to query prefix");
        } catch (Exception e) {
            log.warn("[generateTitle] call python util failed, fallback title. reason={}", e.getMessage());
        }

        // 兜底策略：取用户输入的前 10 个字符当标题
        String fallback = query.length() > 10 ? query.substring(0, 10) : query;
        log.info("[generateTitle] fallback title={}", fallback);
        return fallback;
    }
}

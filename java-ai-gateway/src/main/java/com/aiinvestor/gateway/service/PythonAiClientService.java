package com.aiinvestor.gateway.service;

import com.aiinvestor.gateway.dto.PythonChatRequest;
import com.aiinvestor.gateway.mq.AiChatAuditEvent;
import com.aiinvestor.gateway.mq.AiChatAuditProducer;
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
 * -----------------------------------------------------------
 * 【外交中心：PythonAiClientService】
 * -----------------------------------------------------------
 * 职责：负责 Java 和 Python 两套异构系统的“跨语种”对话。
 * 
 * 核心技术：
 * 1. WebClient: 响应式非阻塞客户端 (对标传统的接口调接口)
 * 2. Sentinel: 服务治理 (熔断、降级、保护)
 * 3. RabbitMQ: 异步审计驱动
 */
@Slf4j
@Service
public class PythonAiClientService {

    private final WebClient pythonAiWebClient;
    private final AiChatAuditProducer auditProducer;

    public PythonAiClientService(WebClient pythonAiWebClient, AiChatAuditProducer auditProducer) {
        this.pythonAiWebClient = pythonAiWebClient;
        this.auditProducer = auditProducer;
    }

    /**
     * 构建 Python 端 LangGraph 识别的统一会话 ID
     */
    public String buildThreadId(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    /**
     * 【大厂风范】SSE 流式能力透传
     * 
     * 知识点解释：
     * 这里我们没有“存数据”，而是做了一个“管道(Pipeline)”。
     * Python 的回答碎成一块块发过来，Java 接住后立刻转递给前端浏览器。
     */
    public Flux<ServerSentEvent<String>> streamChatSse(String message, String userId, String sessionId) {
        String traceId = UUID.randomUUID().toString();
        
        return pythonAiWebClient.post()
                .uri("/ai/v1/chat/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PythonChatRequest(message, userId + ":" + sessionId, traceId))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .doOnNext(event -> {
                    String data = event.data();
                    String preview = data == null ? "null" : (data.length() > 500 ? data.substring(0, 500) + "...(truncated)" : data);
                    log.info("[streamChatSse] python->java event={}, data={}", event.event(), preview);
                })
                .filter(event -> event.data() != null && !event.data().isBlank())
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.event() == null || event.event().isBlank() ? "message" : event.event())
                        .id(event.id())
                        .data(event.data())
                        .build())
                .doFinally(signalType -> {
                    try {
                        auditProducer.send(new AiChatAuditEvent(
                                traceId, userId, sessionId, "/ai/chat/stream", message, Instant.now()
                        ));
                    } catch (Exception ignored) {}
                });
    }

    /**
     * 工具接口：调用 Python LLM 生成漂亮标题
     */
    public String generateTitle(String query) {
        try {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("query", query);

            log.info("[generateTitle] calling python util, query={}", query);

            com.fasterxml.jackson.databind.JsonNode response = pythonAiWebClient.post()
                    .uri("/ai/v1/util/generate_title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                    .block(); // 这里用 .block() 是因为调用时通常在异步任务里，不强制非阻塞

            if (response != null && response.has("data")) {
                String title = response.get("data").get("title").asText();
                log.info("[generateTitle] python returned title={}", title);
                return title;
            }

            log.warn("[generateTitle] python returned empty data, fallback to query prefix");
        } catch (Exception e) {
            log.warn("[generateTitle] call python util failed, fallback title. reason={}", e.getMessage());
        }
        String fallback = query.length() > 10 ? query.substring(0, 10) : query;
        log.info("[generateTitle] fallback title={}", fallback);
        return fallback;
    }
}

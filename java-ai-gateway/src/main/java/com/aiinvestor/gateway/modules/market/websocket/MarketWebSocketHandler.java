package com.aiinvestor.gateway.modules.market.websocket;

import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情 WebSocket 处理器。
 * <p>
 * 职责：
 *   1. 管理客户端 WebSocket 连接的生命周期
 *   2. 接收客户端的订阅/取消订阅请求
 *   3. 定时从 Python 行情服务拉取最新行情并推送给订阅者
 * <p>
 * 协议：
 *   客户端 → 服务端：{"action": "subscribe", "symbols": ["601179", "601231"]}
 *   服务端 → 客户端：{"symbol": "601179", "price": 16.92, "change": -0.23, "changePct": -1.34, "volume": 1213409, "time": "2026-05-02 10:30:00"}
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final com.aiinvestor.gateway.modules.market.service.MarketService marketService;
    private final ObjectMapper objectMapper;

    /** 每个 session 订阅的股票代码列表 */
    private final Map<WebSocketSession, Set<String>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 连接建立后回调，初始化该 session 的订阅列表。
     *
     * @param session 新建立的 WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[WS] 行情连接建立: {}", session.getId());
        subscriptions.put(session, ConcurrentHashMap.newKeySet());
    }

    /**
     * 连接关闭后回调，清理该 session 的订阅数据。
     *
     * @param session 关闭的 WebSocket 会话
     * @param status  关闭状态（正常/异常）
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[WS] 行情连接关闭: {} ({})", session.getId(), status);
        subscriptions.remove(session);
    }

    /**
     * 传输错误回调，清理异常 session 并尝试关闭连接。
     *
     * @param session   出现异常的 WebSocket 会话
     * @param exception 传输异常详情
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WS] 行情连接异常: {} - {}", session.getId(), exception.getMessage());
        subscriptions.remove(session);
        try { session.close(); } catch (Exception e) { log.debug("[WS] 关闭异常连接失败: {}", e.getMessage()); }
    }

    /**
     * 处理客户端文本消息（订阅/取消订阅）。
     * <p>
     * 协议格式：{"action": "subscribe|unsubscribe", "symbols": ["601179", "601231"]}
     *
     * @param session 发送消息的 WebSocket 会话
     * @param message 客户端发来的 JSON 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String action = root.path("action").asText("");
            JsonNode symbolsNode = root.path("symbols");

            if ("subscribe".equalsIgnoreCase(action) && symbolsNode.isArray()) {
                Set<String> subs = subscriptions.get(session);
                if (subs != null) {
                    symbolsNode.forEach(node -> subs.add(node.asText()));
                    log.info("[WS] 订阅更新: session={}, symbols={}", session.getId(), subs);
                }
            } else if ("unsubscribe".equalsIgnoreCase(action) && symbolsNode.isArray()) {
                Set<String> subs = subscriptions.get(session);
                if (subs != null) {
                    symbolsNode.forEach(node -> subs.remove(node.asText()));
                    log.info("[WS] 取消订阅: session={}, symbols={}", session.getId(), subs);
                }
            }
        } catch (Exception e) {
            log.error("[WS] 消息解析失败: {}", e.getMessage());
        }
    }

    /**
     * 定时任务：每 3 秒拉取所有被订阅的行情并推送给客户端。
     * 仅在交易时间执行（9:15 ~ 15:15），避免非交易时段浪费资源。
     */
    @Scheduled(fixedDelay = 3000)
    public void pushQuotes() {
        if (subscriptions.isEmpty()) return;

        // 收集所有被订阅的股票代码
        Set<String> allSymbols = new LinkedHashSet<>();
        for (Set<String> subs : subscriptions.values()) {
            allSymbols.addAll(subs);
        }
        if (allSymbols.isEmpty()) return;

        // 批量拉取行情
        List<MarketQuoteVO> quotes;
        try {
            quotes = marketService.refreshQuotes(new ArrayList<>(allSymbols));
        } catch (Exception e) {
            log.error("[WS] 拉取行情失败: {}", e.getMessage());
            return;
        }

        // 构建 symbol → quote 映射
        Map<String, MarketQuoteVO> quoteMap = new LinkedHashMap<>();
        for (MarketQuoteVO q : quotes) {
            quoteMap.put(q.getSymbol(), q);
        }

        // 推送给每个客户端
        Iterator<Map.Entry<WebSocketSession, Set<String>>> it = subscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<WebSocketSession, Set<String>> entry = it.next();
            WebSocketSession session = entry.getKey();
            Set<String> subs = entry.getValue();

            if (!session.isOpen()) {
                it.remove();
                continue;
            }

            try {
                for (String symbol : subs) {
                    MarketQuoteVO q = quoteMap.get(symbol);
                    if (q == null) continue;

                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("symbol", q.getSymbol());
                    node.put("price", toDouble(q.getLastPrice()));
                    node.put("change", toDouble(q.getChangeAmount()));
                    node.put("changePct", toDouble(q.getChangePercent()));
                    node.put("volume", toDouble(q.getVolume()));
                    node.put("time", q.getQuoteTime() != null
                            ? q.getQuoteTime().format(TIME_FMT)
                            : LocalDateTime.now().format(TIME_FMT));

                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
                }
            } catch (Exception e) {
                log.warn("[WS] 推送失败 session={}: {}", session.getId(), e.getMessage());
                try { session.close(); } catch (Exception closeEx) { log.debug("[WS] 关闭失败会话异常: {}", closeEx.getMessage()); }
                it.remove();
            }
        }
    }

    private double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }
}

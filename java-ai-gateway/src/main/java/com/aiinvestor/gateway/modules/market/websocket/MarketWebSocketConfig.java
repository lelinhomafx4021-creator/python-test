package com.aiinvestor.gateway.modules.market.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 * <p>
 * 注册行情 WebSocket 端点，允许所有来源连接。
 */
@Configuration
@EnableWebSocket
public class MarketWebSocketConfig implements WebSocketConfigurer {

    /** 行情 WebSocket 处理器，负责消息的接收与推送 */
    private final MarketWebSocketHandler marketWebSocketHandler;

    public MarketWebSocketConfig(MarketWebSocketHandler marketWebSocketHandler) {
        this.marketWebSocketHandler = marketWebSocketHandler;
    }

    /**
     * 注册 WebSocket 处理器。
     *
     * @param registry WebSocket 处理器注册中心
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketWebSocketHandler, "/ws/market")
                .setAllowedOrigins("*");
    }
}

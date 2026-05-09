package com.aiinvestor.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ============================================================
 * WebClient 配置 - Java 与 Python AI 的"电话线"
 * ============================================================
 *
 * 技术背景：
 *   WebClient 是 Spring 5 引入的响应式 HTTP 客户端，用来替代传统的
 *   RestTemplate（RestTemplate 已进入维护模式）。
 *
 * 为什么用 WebClient 而不是 RestTemplate？
 *   1. 非阻塞 I/O：基于 Netty，线程不需要等待网络响应，性能更高
 *   2. 流式支持：天然支持 SSE（Server-Sent Events）和 Flux/Mono 响应式编程
 *   3. 函数式 API：链式调用，代码更简洁
 *
 * @author AI Investor Team
 */
@Configuration
@EnableConfigurationProperties(PythonAiProperties.class) // 使 PythonAiProperties 的 @ConfigurationProperties 生效
public class WebClientConfig {

    /**
     * 创建专门用来调 Python AI 服务的 WebClient Bean。
     *
     * 关键配置解读：
     * - baseUrl    : Python 服务的基础地址（从 application.yml 的 python.ai.base-url 读取）
     * - defaultHeader : 请求头默认带 Content-Type: application/json
     * - maxInMemorySize : 内存缓冲区设为 10MB（AI 返回的 JSON 可能很大，默认 256KB 不够）
     *
     * @param properties Python AI 服务配置（baseUrl 等）
     * @return 配置好的 WebClient 实例
     */
    @Bean
    public WebClient pythonAiWebClient(PythonAiProperties properties) {
        return WebClient.builder()
                // Python AI 服务的地址，例如 http://localhost:8000
                .baseUrl(properties.getBaseUrl())
                // 默认告诉 Python 端"我发的是 JSON"
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 扩大缓冲区：AI 投资分析的回答可能很长，需要更大的内存限制
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize()))
                        .build())
                .build();
    }
}

package com.aiinvestor.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则配置。
 *
 * 从 application.yml 中读取 app.sentinel.* 配置项，
 * 避免在 SentinelRuleConfig 中硬编码 QPS 阈值和资源路径。
 *
 * 配置示例：
 * app:
 *   sentinel:
 *     rules:
 *       - resource: /api/v1/market/quotes
 *         qps: 20
 *       - resource: /api/v1/paper/orders
 *         qps: 10
 *       - resource: /gateway/ai/chat/stream
 *         qps: 8
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sentinel")
public class SentinelProperties {

    /**
     * 限流规则列表。
     */
    private List<Rule> rules = new ArrayList<>();

    /**
     * 单条限流规则。
     */
    @Data
    public static class Rule {

        /**
         * 资源路径（接口路径）。
         */
        private String resource;

        /**
         * QPS 阈值。
         */
        private double qps;
    }
}

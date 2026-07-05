package com.aiinvestor.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置属性。
 *
 * 从 application.yml 中读取 app.cors.* 配置项，
 * 避免在 SecurityConfig / WebMvcConfig 中硬编码前端地址。
 *
 * 配置示例：
 * app:
 *   cors:
 *     allowed-origins: http://localhost:5173,http://127.0.0.1:5173
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 允许的前端来源地址，多个用逗号分隔。
     * 通过环境变量 APP_CORS_ORIGINS 可在部署时覆盖。
     */
    private String allowedOrigins = "http://localhost:[*],http://127.0.0.1:[*],https://*.trycloudflare.com";

    /**
     * 解析为 List，方便直接使用。
     */
    public List<String> getAllowedOriginList() {
        return Arrays.asList(allowedOrigins.split(","));
    }
}

package com.aiinvestor.gateway.config;

import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * Returns consistent JSON when Sentinel blocks a request.
 */
@Configuration
public class SentinelBlockHandlerConfig {

    @Bean
    public BlockExceptionHandler sentinelBlockExceptionHandler(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, String resource, BlockException ex) -> {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "1");

            String path = request.getRequestURI();
            String message = resolveMessage(path, resource);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(429, message)));
        };
    }

    private String resolveMessage(String path, String resource) {
        String target = path == null || path.isBlank() ? resource : path;
        if (target == null) {
            return "请求过于频繁，请稍后再试";
        }
        if (target.contains("/market/quotes")) {
            return "行情查询过于频繁，请稍后刷新";
        }
        if (target.contains("/paper/orders")) {
            return "下单请求过于频繁，请稍后再试";
        }
        if (target.contains("/chat/stream")) {
            return "AI 会话繁忙，请稍后重新发起对话";
        }
        return "请求过于频繁，请稍后再试";
    }
}

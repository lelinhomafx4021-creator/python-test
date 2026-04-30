package com.aiinvestor.gateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ============================================================
 * Python AI 服务连接配置
 * ============================================================
 *
 * 作用：
 *   从 application.yml 中读取 `python.ai.base-url` 配置，
 *   为 WebClient 提供 Python 服务的连接地址。
 *
 * 技术点：
 *   @ConfigurationProperties 是 Spring Boot 类型安全配置的核心：
 *   - 自动将 yml/properties 中的配置映射到 Java 字段
 *   - 支持嵌套对象、List、Map 等复杂类型
 *   - 配合 @Validated 可在启动时校验配置值是否正确
 *
 * 使用方式：
 *   在 application.yml 中配置：
 *   python:
 *     ai:
 *       base-url: http://localhost:8000
 *
 * @author AI Investor Team
 */
@Data                                              // Lombok：自动生成 getter/setter/toString/equals/hashCode
@Validated                                         // 启用 JSR-303 参数校验（@NotBlank 会生效）
@ConfigurationProperties(prefix = "python.ai")      // 绑定 python.ai.* 配置项
public class PythonAiProperties {

    /**
     * Python AI 服务的基础 URL。
     *
     * @NotBlank : 启动时如果此值为空或仅空格，Spring 会拒绝启动。
     *             这是一种"快速失败"策略——发现配置问题立即报错，而不是运行中崩溃。
     */
    @NotBlank
    private String baseUrl;
}

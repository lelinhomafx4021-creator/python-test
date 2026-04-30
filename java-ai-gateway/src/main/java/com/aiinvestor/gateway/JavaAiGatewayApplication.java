package com.aiinvestor.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

/**
 * ============================================================
 * AI 投资助手 - Java 网关启动入口
 * ============================================================
 *
 * 架构定位：
 *   这是整个 Java 后端的启动类。Java 网关层充当 Spring Boot 微服务，
 *   负责鉴权、会话管理、SSE 流式透传，以及将请求转发给 Python AI 引擎。
 *
 * 技术要点：
 *   1. @SpringBootApplication 是 Spring Boot 核心注解，包含三大能力：
 *      - @Configuration  : 标记配置类
 *      - @EnableAutoConfiguration : 自动装配（根据 classpath 依赖自动配置）
 *      - @ComponentScan  : 自动扫描当前包及子包下的组件
 *   2. 排除了 RabbitAutoConfiguration，因为 RabbitMQ 在本地开发时
 *      可能未启动，直接自动装配会导致启动失败。
 *
 * @author AI Investor Team
 */
@SpringBootApplication(exclude = {RabbitAutoConfiguration.class})
public class JavaAiGatewayApplication {
    /**
     * 主入口方法。
     * SpringApplication.run() 会启动内嵌 Tomcat 容器，
     * 初始化整个 Spring IoC 容器，并监听 HTTP 请求。
     *
     * @param args 命令行参数（可通过 --server.port=8080 等覆盖配置）
     */
    public static void main(String[] args) {
        SpringApplication.run(JavaAiGatewayApplication.class, args);
    }
}

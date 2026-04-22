package com.aiinvestor.gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${app.mq.audit-exchange}")
    private String auditExchange;

    @Value("${app.mq.audit-routing-key}")
    private String auditRoutingKey;

    public static final String AUDIT_QUEUE = "ai.chat.audit.queue";

    /**
     * 定义一个直接交换机 (Direct Exchange)
     */
    @Bean
    public DirectExchange auditExchange() {
        return new DirectExchange(auditExchange);
    }

    /**
     * 定义审计队列
     */
    @Bean
    public Queue auditQueue() {
        return new Queue(AUDIT_QUEUE);
    }

    /**
     * 将队列和交换机绑定，并指定路由键
     */
    @Bean
    public Binding auditBinding(Queue auditQueue, DirectExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(auditRoutingKey);
    }

    /**
     * 【面试加分】配置 RabbitMQ 消息序列化为 JSON
     * 否则默认是 Java 原生序列化，可读性差且跨语言支持差
     */
    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }
}

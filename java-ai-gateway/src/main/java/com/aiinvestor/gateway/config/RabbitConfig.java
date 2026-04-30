package com.aiinvestor.gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================
 * RabbitMQ 消息队列配置
 * ============================================================
 *
 * 业务背景：
 *   用户每次使用 AI 聊天功能后，系统需要记录一条审计日志（谁、什么时候、
 *   问了什么、走哪个接口）。但这个"记日志"操作不应该阻塞用户的聊天响应。
 *   所以采用消息队列异步处理——主流程只负责发消息，消费者在后台慢慢入库。
 *
 * 三个核心概念（面试必考）：
 *   1. Exchange（交换机）: 接收消息并根据路由规则分发给队列
 *   2. Queue（队列）   : 存储消息的缓冲区
 *   3. Binding（绑定） : 定义 Exchange 和 Queue 之间的路由规则
 *
 * @author AI Investor Team
 */
@Configuration
public class RabbitConfig {

    /** 从配置文件读取交换机名称 */
    @Value("${app.mq.audit-exchange}")
    private String auditExchange;

    /** 从配置文件读取路由键（routing key） */
    @Value("${app.mq.audit-routing-key}")
    private String auditRoutingKey;

    /** 审计队列的固定名称（硬编码但合理，因为只有这一个消费者） */
    public static final String AUDIT_QUEUE = "ai.chat.audit.queue";

    /**
     * 定义 Direct Exchange（直接交换机）。
     *
     * Direct Exchange 的路由规则：
     *   Binding 时指定 routing key，生产者发消息时也带 routing key，
     *   只有完全匹配的队列才能收到消息。
     *
     * 其他交换机类型：Fanout（广播）、Topic（模式匹配）、Headers（头匹配）
     *
     * @return DirectExchange 实例
     */
    @Bean
    public DirectExchange auditExchange() {
        return new DirectExchange(auditExchange);
    }

    /**
     * 定义审计队列。
     * 队列名称固定，消费者通过此名称监听消息。
     *
     * @return Queue 实例
     */
    @Bean
    public Queue auditQueue() {
        return new Queue(AUDIT_QUEUE);
    }

    /**
     * 将队列绑定到交换机。
     *
     * BindingBuilder.bind(auditQueue).to(auditExchange).with(auditRoutingKey)
     * 的意思是：
     *   "把 auditQueue 绑到 auditExchange 上，只有 routing key 匹配
     *    auditRoutingKey 的消息才会被路由到这个队列。"
     *
     * @param auditQueue    上面定义的队列 Bean
     * @param auditExchange 上面定义的交换机 Bean
     * @return Binding 绑定关系
     */
    @Bean
    public Binding auditBinding(Queue auditQueue, DirectExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(auditRoutingKey);
    }

    /**
     * 【重要】配置消息序列化为 JSON 格式。
     *
     * 默认情况下 RabbitMQ 使用 Java 原生序列化（ObjectOutputStream），问题：
     *   1. 可读性差：消息体是二进制，无法用 RabbitMQ 管理界面直接查看
     *   2. 跨语言不兼容：Python/Go 消费者无法反序列化 Java 序列化的数据
     *   3. 耦合度高：Java 类改名就反序列化失败
     *
     * Jackson2JsonMessageConverter 解决了以上所有问题。
     *
     * @return JSON 消息转换器
     */
    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }
}

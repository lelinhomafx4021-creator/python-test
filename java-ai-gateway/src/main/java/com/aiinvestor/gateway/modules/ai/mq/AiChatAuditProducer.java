package com.aiinvestor.gateway.modules.ai.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * ============================================================
 * 审计日志消息生产者
 * ============================================================
 *
 * 职责：
 *   将审计事件发送到 RabbitMQ，由消费者异步入库。
 *   生产者不关心消息何时被消费、是否成功入库——完全解耦。
 *
 * 设计考量：
 *   RabbitTemplate 使用 @Autowired(required = false)，
 *   这样当本地开发没有启动 RabbitMQ 时，Bean 为 null，
 *   send() 方法会静默跳过，不会报错阻塞主流程。
 *   这是一种"优雅降级"策略。
 *
 * @author AI Investor Team
 */
@Component
public class AiChatAuditProducer {

    @Nullable
    private final RabbitTemplate rabbitTemplate;
    private final String auditExchange;
    private final String auditRoutingKey;

    public AiChatAuditProducer(@Nullable RabbitTemplate rabbitTemplate,
                               @Value("${app.mq.audit-exchange:}") String auditExchange,
                               @Value("${app.mq.audit-routing-key:}") String auditRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.auditExchange = auditExchange;
        this.auditRoutingKey = auditRoutingKey;
    }

    /**
     * 发送审计事件到 RabbitMQ。
     *
     * convertAndSend 方法会自动使用配置好的 Jackson2JsonMessageConverter
     * 将 Java 对象序列化为 JSON 字符串发送。
     *
     * @param event 审计事件对象（不可变的 Record）
     */
    public void send(AiChatAuditEvent event) {
        // 防御性编程：RabbitTemplate 为 null 时静默跳过
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(auditExchange, auditRoutingKey, event);
        }
    }
}

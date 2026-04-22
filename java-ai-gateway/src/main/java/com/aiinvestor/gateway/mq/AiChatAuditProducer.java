package com.aiinvestor.gateway.mq;

import com.aiinvestor.gateway.mq.AiChatAuditEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 审计日志发送者
 * 规范：所有的消息中间件外发逻辑统一在 mq 包下
 */
@Component
public class AiChatAuditProducer {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Value("${app.mq.audit-exchange:}")
    private String auditExchange;

    @Value("${app.mq.audit-routing-key:}")
    private String auditRoutingKey;

    public void send(AiChatAuditEvent event) {
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(auditExchange, auditRoutingKey, event);
        }
    }
}

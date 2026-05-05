package com.aiinvestor.gateway.modules.papertrading.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ============================================================
 * 交易流水事件消息生产者
 * ============================================================
 *
 * 职责：
 *   将交易事件发送到 RabbitMQ，由消费者异步入库并生成用户通知。
 *   生产者不关心消息何时被消费、是否成功入库——完全解耦。
 *
 * 设计考量：
 *   RabbitTemplate 使用 @Autowired(required = false)，
 *   与 AiChatAuditProducer 保持一致：本地开发没有 RabbitMQ 时
 *   Bean 为 null，send() 方法静默跳过，不阻塞主流程。
 *
 * @author AI Investor Team
 */
@Component
public class TransactionEventProducer {

    /**
     * RabbitMQ 操作模板。
     * required=false：本地不启动 RabbitMQ 也不会导致项目启动失败。
     */
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    /** 交换机名称（从配置文件读取） */
    @Value("${app.mq.transaction-exchange:}")
    private String transactionExchange;

    /** 路由键（从配置文件读取） */
    @Value("${app.mq.transaction-routing-key:}")
    private String transactionRoutingKey;

    /**
     * 发送交易事件到 RabbitMQ。
     *
     * @param event 交易事件对象（不可变的 Record）
     */
    public void send(TransactionEvent event) {
        // 防御性编程：RabbitTemplate 为 null 时静默跳过
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(transactionExchange, transactionRoutingKey, event);
        }
    }
}

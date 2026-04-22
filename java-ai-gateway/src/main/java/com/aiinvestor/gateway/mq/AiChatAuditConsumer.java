package com.aiinvestor.gateway.mq;

import com.aiinvestor.gateway.dao.entity.AiChatAuditDO;
import com.aiinvestor.gateway.dao.mapper.AiChatAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AiChatAuditConsumer {

    private final AiChatAuditMapper auditMapper;

    public AiChatAuditConsumer(AiChatAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }
    /**
     * 监听 RabbitMQ 队列：当有审计数据进入队列时触发
     * (开发调试中暫时屏蔽，防止连不上远程 MQ 报错)
     */
    // @RabbitListener(queues = "ai.chat.audit.queue")
    public void onMessage(AiChatAuditEvent event) {
        // 第一步：打印日志，方便在控制台实时观察审计动态
        log.info("[审计中心] 正在异步持久化 -> TraceId: {}", event.traceId());
        
        // 第二步：将 MQ 的 Event 对象转换为数据库的 DO 对象
        // 使用 Builder 模式（Lombok 提供），链式调用非常优雅，面试首选！
        AiChatAuditDO auditDO = AiChatAuditDO.builder()
                .traceId(event.traceId())
                .userId(event.userId())
                .sessionId(event.sessionId())
                .endpoint(event.endpoint())
                .message(event.message())
                .createdAt(LocalDateTime.now()) // 记录落库时间
                .build();

        // 第三步：执行插入 SQL。MyBatis-Plus 的 insert 会自动转换字段（如 traceId -> trace_id）
        auditMapper.insert(auditDO);
        
        log.info("[审计中心] 持久化成功！id={}", auditDO.getId());
    }
}

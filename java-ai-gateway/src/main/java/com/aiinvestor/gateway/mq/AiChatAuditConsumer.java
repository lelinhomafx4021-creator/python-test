package com.aiinvestor.gateway.mq;

import com.aiinvestor.gateway.dao.entity.AiChatAuditDO;
import com.aiinvestor.gateway.dao.mapper.AiChatAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ============================================================
 * 审计日志消息消费者
 * ============================================================
 *
 * 职责：
 *   监听 RabbitMQ 中的审计队列，将收到的审计事件异步写入数据库。
 *
 * 架构优势：
 *   1. 解耦：主业务（聊天响应）和审计记录完全分离
 *   2. 削峰：高并发时，消息在队列中排队，消费者按自己的速度处理
 *   3. 容错：消费者挂了不影响主流程，消息在队列中等待
 *
 * 注意：
 *   @RabbitListener 目前被注释掉了，因为本地开发时 RabbitMQ 可能未启动。
 *   生产环境去掉注释即可启用。
 *
 * @author AI Investor Team
 */
@Slf4j
@Service
public class AiChatAuditConsumer {

    /** 审计流水表 Mapper */
    private final AiChatAuditMapper auditMapper;

    public AiChatAuditConsumer(AiChatAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    /**
     * 监听审计队列，消费消息。
     *
     * 处理流程：
     *   1. 打印日志：方便在控制台实时观察审计动态
     *   2. Event → DO 转换：将消息体转为数据库实体
     *   3. 入库：调用 MyBatis-Plus 的 insert 方法
     *
     * @param event 从队列中消费到的审计事件
     */
    // @RabbitListener(queues = "ai.chat.audit.queue")  // 本地开发时注释，上线前取消注释
    public void onMessage(AiChatAuditEvent event) {
        // 步骤 1：记录消费日志
        log.info("[审计中心] 正在异步持久化 -> TraceId: {}", event.traceId());

        // 步骤 2：将不可变的 Event（Record）转换为可变的 DO（Entity）
        // 使用 Lombok 的 @Builder 生成器模式，链式调用，代码清晰
        AiChatAuditDO auditDO = AiChatAuditDO.builder()
                .traceId(event.traceId())            // 追踪 ID
                .userId(event.userId())              // 用户 ID
                .sessionId(event.sessionId())        // 会话 ID
                .endpoint(event.endpoint())          // 接口路径
                .message(event.message())            // 消息内容
                .createdAt(LocalDateTime.now())      // 落库时间（取当前时刻）
                .build();

        // 步骤 3：执行 INSERT SQL
        // MyBatis-Plus 自动处理驼峰转下划线（traceId → trace_id）
        auditMapper.insert(auditDO);

        log.info("[审计中心] 持久化成功！id={}", auditDO.getId());
    }
}

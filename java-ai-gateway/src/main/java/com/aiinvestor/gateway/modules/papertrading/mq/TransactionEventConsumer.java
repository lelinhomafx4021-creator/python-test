package com.aiinvestor.gateway.modules.papertrading.mq;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.TransactionLogDO;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.TransactionLogMapper;
import com.aiinvestor.gateway.modules.shared.service.UserNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ============================================================
 * 交易事件消息消费者
 * ============================================================
 *
 * 职责：
 *   监听 RabbitMQ 中的交易事件队列，将收到的交易事件异步写入数据库，
 *   同时为用户创建一条通知记录。
 *
 * 架构优势：
 *   1. 解耦：主业务（下单/充值/提现）和流水记录完全分离
 *   2. 削峰：高并发时，消息在队列中排队，消费者按自己的速度处理
 *   3. 容错：消费者挂了不影响主流程，消息在队列中等待
 *
 * 注意：
 *   @RabbitListener 监听 transaction.event.queue 队列，处理模拟交易事件。
 *   生产环境去掉注释即可启用。
 *
 * @author AI Investor Team
 */
@Slf4j
@Service
public class TransactionEventConsumer {

    /** 交易流水日志 Mapper */
    private final TransactionLogMapper transactionLogMapper;

    /** 用户通知服务 */
    private final UserNotificationService userNotificationService;

    public TransactionEventConsumer(TransactionLogMapper transactionLogMapper,
                                    UserNotificationService userNotificationService) {
        this.transactionLogMapper = transactionLogMapper;
        this.userNotificationService = userNotificationService;
    }

    /**
     * 监听交易事件队列，消费消息。
     *
     * 处理流程：
     *   1. 打印日志：方便在控制台实时观察交易动态
     *   2. Event → DO 转换：将消息体转为数据库实体
     *   3. 入库：调用 MyBatis-Plus 的 insert 方法
     *   4. 创建通知：为用户生成一条交易通知
     *
     * @param event 从队列中消费到的交易事件
     */
    @RabbitListener(queues = "transaction.event.queue")
    public void onMessage(TransactionEvent event) {
        // 步骤 1：记录消费日志
        log.info("[交易流水] 正在异步持久化 -> userId: {}, eventType: {}, symbol: {}",
                event.userId(), event.eventType(), event.symbol());

        // 步骤 2：将不可变的 Event（Record）转换为可变的 DO（Entity）
        TransactionLogDO logDO = TransactionLogDO.builder()
                .userId(event.userId())
                .eventType(event.eventType())
                .symbol(event.symbol())
                .side(event.side())
                .quantity(event.quantity())
                .price(event.price())
                .amount(event.amount())
                .balanceAfter(event.balanceAfter())
                .description(event.description())
                .createdAt(LocalDateTime.now())
                .build();

        // 步骤 3：执行 INSERT SQL
        transactionLogMapper.insert(logDO);

        log.info("[交易流水] 持久化成功！id={}", logDO.getId());

        // 步骤 4：为用户创建交易通知
        String category = "trade";
        String title = buildNotificationTitle(event.eventType());
        String content = buildNotificationContent(event);
        userNotificationService.createNotification(event.userId(), category, title, content);
    }

    /**
     * 根据事件类型生成通知标题。
     */
    private String buildNotificationTitle(String eventType) {
        return switch (eventType) {
            case "ORDER_PLACED" -> "委托已提交";
            case "ORDER_FILLED" -> "委托已成交";
            case "ORDER_CANCELLED" -> "委托已撤销";
            case "DEPOSIT" -> "充值到账";
            case "WITHDRAW" -> "提现成功";
            default -> "交易通知";
        };
    }

    /**
     * 根据事件详情生成通知内容。
     */
    private String buildNotificationContent(TransactionEvent event) {
        return switch (event.eventType()) {
            case "ORDER_PLACED" -> String.format("已提交%s %s 委托，数量：%d",
                    event.side(), event.symbol(), event.quantity());
            case "ORDER_FILLED" -> String.format("%s %s 已成交，价格：%.4f，数量：%d，金额：%.2f",
                    event.side(), event.symbol(), event.price(), event.quantity(), event.amount());
            case "ORDER_CANCELLED" -> String.format("%s %s 委托已撤销", event.side(), event.symbol());
            case "DEPOSIT" -> String.format("充值 %.2f 元，当前余额：%.2f 元",
                    event.amount(), event.balanceAfter());
            case "WITHDRAW" -> String.format("提现 %.2f 元，当前余额：%.2f 元",
                    event.amount(), event.balanceAfter());
            default -> event.description() != null ? event.description() : "交易流水更新";
        };
    }
}

# 11 - RabbitMQ 消息队列

## 一、RabbitMQ 是什么

**一句话：一个"中间人快递站"。**

生产者把包裹（消息）扔进快递站，消费者从快递站取包裹处理。双方互不认识，也不需要同时在线。

```
没有 MQ：
  生产者 ──────────────────→ 消费者
  （必须同时在线，直接调用）

有 MQ：
  生产者 ──→ [ 队列 ] ──→ 消费者
  （扔下就走，消费者想什么时候取都行）
```

**Java 类比**：`CompletableFuture` 可以异步执行任务，但进程挂了任务就丢了。RabbitMQ 的消息存在**独立的服务器**里，进程重启后还能取到。

---

## 二、为什么需要消息队列

### 场景：用户发了一条 AI 聊天消息

```
没有 MQ（同步）：
  用户提问 → 调 Python AI → AI 返回 → 写审计日志到 DB → 返回给用户
                                             ↑
                                        这一步要 50ms，用户干等着

有 MQ（异步）：
  用户提问 → 调 Python AI → AI 返回 → 扔进 MQ → 立即返回给用户
                                                ↓
                                       消费者在后台慢慢写 DB（用户不感知）
```

### 三个核心好处（面试必考）

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  1. 解耦（Decoupling）                                     │
│     主业务不知道、也不关心谁来消费消息                        │
│     以后加新的消费者（比如通知服务），主业务一行代码都不用改    │
│                                                            │
│  2. 削峰（Peak Shaving）                                   │
│     瞬间 1000 条消息进来，队列排队                           │
│     消费者按自己的速度处理，不会被压垮                       │
│                                                            │
│  3. 容错（Fault Tolerance）                                │
│     消费者挂了，消息在队列里等着，不会丢                     │
│     消费者恢复后继续消费，不影响主流程                       │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 三、核心概念（快递站类比）

```
┌──────────────────────────────────────────────────────────────┐
│                     RabbitMQ 服务器                            │
│                                                              │
│   ┌─────────────┐    routing key     ┌───────────────────┐  │
│   │  Exchange    │ ─────────────────→ │  Queue (审计队列)  │  │
│   │  (快递分拣)  │   "audit.key"      │  (快递柜)          │  │
│   └──────▲──────┘                    └────────┬──────────┘  │
│          │                                    │              │
└──────────┼────────────────────────────────────┼──────────────┘
           │                                    │
     Producer                              Consumer
   (生产者/发件人)                         (消费者/收件人)
```

| 概念 | 快递类比 | 说明 |
|------|---------|------|
| **Producer** | 发件人 | 发送消息的应用程序 |
| **Exchange** | 快递分拣中心 | 接收消息，按规则分发给队列 |
| **Routing Key** | 快递单上的地址标签 | 消息的"投递地址" |
| **Queue** | 快递柜 | 存储消息的缓冲区 |
| **Binding** | 分拣规则 | 定义"哪个地址送哪个柜子" |
| **Consumer** | 收件人 | 从队列取消息并处理 |

---

## 四、Exchange 的四种类型

### 1. Direct Exchange（精确匹配）

```
routing key = "audit.key"
  → 只有绑定了 "audit.key" 的队列收到消息

项目用的就是这种，最简单最常用。
```

### 2. Topic Exchange（模式匹配）

```
routing key = "order.pay.success"
  → 绑定了 "order.*.*" 的队列能收到
  → 绑定了 "order.#" 的队列也能收到

通配符：
  *  匹配一个单词
  #  匹配零个或多个单词

适合按业务类型分发，比如：
  "order.pay.*"   → 支付队列
  "order.refund.*" → 退款队列
```

### 3. Fanout Exchange（广播）

```
不管 routing key，所有绑定了的队列都收到消息
  → 类似广播电台，谁都能听

适合通知场景，比如同时通知审计 + 通知 + 统计。
```

### 4. Headers Exchange（头匹配）

```
按消息头（Headers）的键值匹配，不看 routing key
  → 很少用，了解即可
```

### 对比总结

| 类型 | 路由规则 | 使用场景 |
|------|---------|---------|
| Direct | routing key 精确匹配 | 点对点，最常用 |
| Topic | routing key 模式匹配（* #） | 按业务规则分发 |
| Fanout | 广播给所有绑定队列 | 通知、日志广播 |
| Headers | 按消息头匹配 | 很少用 |

---

## 五、Spring Boot 整合 RabbitMQ

### 1. 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2. 配置连接信息

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
```

### 3. 定义 Exchange + Queue + Binding

```java
@Configuration
public class RabbitConfig {

    // 交换机名称
    public static final String EXCHANGE = "ai.exchange";
    // 队列名称
    public static final String QUEUE = "ai.chat.audit.queue";
    // 路由键
    public static final String ROUTING_KEY = "audit.key";

    // 定义 Direct Exchange
    @Bean
    public DirectExchange auditExchange() {
        return new DirectExchange(EXCHANGE);
    }

    // 定义队列
    @Bean
    public Queue auditQueue() {
        return new Queue(QUEUE);
    }

    // 绑定：交换机 + 队列 + routing key
    @Bean
    public Binding auditBinding(Queue auditQueue, DirectExchange auditExchange) {
        return BindingBuilder.bind(auditQueue)    // 队列
                .to(auditExchange)                 // 绑到交换机
                .with(ROUTING_KEY);                // routing key
    }
}
```

**这段代码的含义**："把 `auditQueue` 绑到 `auditExchange` 上，只有 routing key 等于 `audit.key` 的消息才会被路由到这个队列。"

### 4. 发送消息（Producer）

```java
@Component
public class AuditProducer {

    private final RabbitTemplate rabbitTemplate;

    public AuditProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(AuditEvent event) {
        // 参数：交换机、routing key、消息体
        rabbitTemplate.convertAndSend("ai.exchange", "audit.key", event);
        // convertAndSend 会自动把 Java 对象序列化成 JSON
    }
}
```

### 5. 接收消息（Consumer）

```java
@Component
public class AuditConsumer {

    @RabbitListener(queues = "ai.chat.audit.queue")  // 监听哪个队列
    public void onMessage(AuditEvent event) {
        // 自动反序列化 JSON → Java 对象
        System.out.println("收到审计事件：" + event);
        // 写入数据库...
    }
}
```

### 完整调用链

```java
// 业务代码里只需要一行：
auditProducer.send(new AuditEvent(userId, message, ...));

// 之后的事由 MQ 自动完成：
// Producer → Exchange → Queue → Consumer → DB
```

---

## 六、消息序列化

### 默认序列化的问题

```
默认：Java 原生序列化（ObjectOutputStream）
  ❌ 二进制格式，RabbitMQ 管理界面看不懂
  ❌ Python/Go 消费者无法反序列化
  ❌ Java 类改名就反序列化失败
```

### 解决方案：JSON 序列化

```java
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

加了这个 Bean 后，`convertAndSend` 自动把对象转成 JSON，`onMessage` 自动把 JSON 转回对象。

---

## 七、项目中的实际用法

### 场景 1：AI 聊天审计

```
用户发消息 → Controller 调 AI → 返回回答
                │
                └→ auditProducer.send(event)  ← 异步，用户不等
                       │
                       ▼
                  MQ: ai.chat.audit.queue
                       │
                       ▼
                  auditConsumer → 写入 MySQL
```

### 场景 2：交易事件通知

```
用户下单 → PaperTradingService 处理订单 → 返回结果
                │
                └→ transactionProducer.send(event)  ← 异步
                       │
                       ▼
                  MQ: transaction.event.queue
                       │
                       ▼
                  transactionConsumer → 写入 transaction_logs 表
```

### 代码对应关系

| 角色 | 文件 | 关键代码 |
|------|------|---------|
| 配置 | `RabbitConfig.java` | 定义 Exchange、Queue、Binding |
| 生产者 | `AiChatAuditProducer.java` | `rabbitTemplate.convertAndSend()` |
| 消费者 | `AiChatAuditConsumer.java` | `@RabbitListener(queues = "...")` |
| 消息体 | `AiChatAuditEvent.java` | `record` 类型，不可变数据载体 |

---

## 八、一个设计细节：优雅降级

```java
@Component
public class AiChatAuditProducer {

    @Nullable
    private final RabbitTemplate rabbitTemplate;  // 可能为 null

    public void send(AiChatAuditEvent event) {
        if (rabbitTemplate != null) {        // 没有 MQ 就跳过
            rabbitTemplate.convertAndSend(...);
        }
    }
}
```

**为什么？** 本地开发时可能没启动 RabbitMQ 容器。如果直接注入 `RabbitTemplate`，Spring 会报错启动失败。加 `@Nullable` + null 判断，没 MQ 就静默跳过，不影响主流程。这叫**优雅降级**。

---

## 九、消息确认机制（Ack）

### 问题：消息发出去了，但消费者处理失败了怎么办？

RabbitMQ 有两层确认：

```
1. 生产者确认（Publisher Confirm）
   消息到达交换机 → RabbitMQ 回调确认
   消息到不了交换机 → RabbitMQ 回调失败

2. 消费者确认（Consumer Ack）
   消费者处理成功 → 手动 ack → RabbitMQ 删除消息
   消费者处理失败 → nack/reject → 消息重回队列（或进死信队列）
```

### 三种确认模式

| 模式 | 行为 | 安全性 |
|------|------|--------|
| AUTO（默认） | 消息送到消费者就自动 ack，不管有没有处理成功 | 低：处理失败消息就丢了 |
| MANUAL | 消费者自己决定什么时候 ack/nack | 高：完全可控 |
| NONE | 不确认，消息发了就不管 | 最低 |

### 手动 Ack 代码

配置文件：
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual   # 手动确认
        prefetch: 1                # 一次只取一条，处理完再取下一条
        default-requeue-rejected: false  # 拒绝后不重回原队列（进死信）
```

消费者代码：
```java
@RabbitListener(queues = "ai.chat.audit.queue")
public void onMessage(AiChatAuditEvent event, Channel channel,
                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    try {
        // 业务处理
        auditMapper.insert(转换成DO);

        // 处理成功，手动 ack
        // 参数：tag=消息编号, multiple=false（只确认这一条）
        channel.basicAck(tag, false);

    } catch (Exception e) {
        // 处理失败，拒绝消息
        // 参数：tag=消息编号, multiple=false, requeue=false（不重回原队列，进死信）
        channel.basicNack(tag, false, false);
    }
}
```

### Ack 三个方法对比

```
basicAck(tag, multiple)     → 确认成功，消息删除
basicNack(tag, multiple, requeue)  → 确认失败
basicReject(tag, requeue)   → 确认失败（只能处理单条）

requeue = true  → 消息重回原队列（会再次被消费，可能死循环）
requeue = false → 消息不重回（如果配了死信队列，就进死信）
```

---

## 十、死信队列（Dead Letter Queue）

### 什么情况下消息会变成"死信"？

```
1. 消费者拒绝消息（nack/reject）且 requeue=false
2. 消息过期（TTL 到期，没人消费）
3. 队列满了，新消息进不来
```

### 死信队列的作用

```
正常队列处理失败
  → 消息进入死信队列
  → 专门的消费者处理死信（报警、人工介入、重试）
```

**类比**：快递柜里超时没人取的包裹 → 转到"异常件处理区"。

### 完整配置代码

```java
@Configuration
public class DlqConfig {

    // ============ 死信交换机 + 死信队列 ============

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("ai.dlx.exchange");  // 死信交换机
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable("ai.chat.audit.dlq")  // 死信队列
                .build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlqQueue())
                .to(dlxExchange())
                .with("dlx.routing.key");
    }

    // ============ 正常队列绑定死信 ============

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable("ai.chat.audit.queue")
                .deadLetterExchange("ai.dlx.exchange")      // 死信发到这个交换机
                .deadLetterRoutingKey("dlx.routing.key")     // 死信的 routing key
                .ttl(60000)                                   // 消息 60 秒没人消费也变死信
                .build();
    }
}
```

### 死信消费者

```java
@Component
public class DlqConsumer {

    @RabbitListener(queues = "ai.chat.audit.dlq")
    public void onDlqMessage(AiChatAuditEvent event) {
        // 记录到死信日志表（方便排查）
        log.error("[死信队列] 审计消息处理失败：traceId={}", event.traceId());

        // 方案1：报警通知运维
        // 方案2：人工介入处理
        // 方案3：延迟后重新投递（见下方重试机制）
    }
}
```

### 流程图

```
Producer 发消息
  → 正常队列: ai.chat.audit.queue
  → Consumer 处理失败
  → basicNack(tag, false, false)
  → 消息变成死信
  → 死信交换机: ai.dlx.exchange
  → 死信队列: ai.chat.audit.dlq
  → DlqConsumer 处理（报警/记录/重试）
```

---

## 十一、重试机制

### 方案 1：Spring Retry（推荐，最简单）

依赖：
```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

配置重试拦截器：
```java
@Configuration
@EnableRetry
public class RabbitRetryConfig {

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return Interceptors.amqpRetry()
                .retryPolicy(new SimpleRetryPolicy(3))         // 最多重试 3 次
                .backOffPolicy(new ExponentialBackOffPolicy()) // 指数退避
                .build();
    }
}
```

消费者绑定重试：
```java
@RabbitListener(queues = "ai.chat.audit.queue",
               containerFactory = "retryContainerFactory")
public void onMessage(AiChatAuditEvent event) {
    // 重试 3 次都失败后 → 自动 nack → 进死信队列
    auditMapper.insert(转换成DO);
}
```

### 指数退避策略

```
第 1 次失败 → 等 1 秒后重试
第 2 次失败 → 等 2 秒后重试
第 3 次失败 → 等 4 秒后重试
第 3 次还是失败 → 放弃，进死信队列
```

**为什么要指数退避？** 如果是数据库暂时不可用，立刻重试大概率还是失败。等一会儿再试，数据库可能就恢复了。

### 方案 2：手动重试（灵活但代码多）

```java
@RabbitListener(queues = "ai.chat.audit.queue")
public void onMessage(AiChatAuditEvent event, Channel channel,
                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    int retryCount = 3;

    for (int i = 0; i < retryCount; i++) {
        try {
            auditMapper.insert(转换成DO);
            channel.basicAck(tag, false);  // 成功，确认
            return;

        } catch (Exception e) {
            log.warn("第 {} 次重试失败", i + 1);
            if (i < retryCount - 1) {
                Thread.sleep((long) Math.pow(2, i) * 1000);  // 指数退避
            }
        }
    }

    // 全部失败，进死信
    channel.basicNack(tag, false, false);
}
```

### 方案 3：延迟插件重试（最优雅）

安装 RabbitMQ 延迟插件后，可以实现"延迟 N 秒后重新投递"：

```java
@Bean
public Queue retryQueue() {
    return QueueBuilder.durable("ai.chat.audit.retry.queue")
            .ttl(10000)                                    // 消息停留 10 秒
            .deadLetterExchange("ai.exchange")             // 到期后发回正常交换机
            .deadLetterRoutingKey("audit.key")             // 重新走正常流程
            .build();
}
```

```
Consumer 处理失败
  → 消息进 retry 队列（等 10 秒）
  → 10 秒后自动回到正常队列
  → Consumer 再次消费（相当于延迟重试）
```

---

## 十二、生产级完整流程

把上面所有机制组合起来：

```
Producer 发消息
  │
  ├─ Publisher Confirm → 确认消息到达交换机
  │
  ▼
正常队列 (ai.chat.audit.queue)
  │
  ▼
Consumer 取消息（prefetch=1，一次一条）
  │
  ├─ 成功 → basicAck → 消息删除 ✅
  │
  └─ 失败 → Spring Retry 重试 3 次（指数退避）
               │
               ├─ 第 1 次失败 → 等 1s 重试
               ├─ 第 2 次失败 → 等 2s 重试
               ├─ 第 3 次失败 → 等 4s 重试
               │
               └─ 3 次都失败 → basicNack → 进死信队列
                                        │
                                        ▼
                                   死信队列 (ai.chat.audit.dlq)
                                        │
                                        ▼
                                   DlqConsumer
                                        │
                                        ├─ 记录到死信日志表
                                        ├─ 报警通知运维
                                        └─ 人工介入 / 延迟重投
```

### 配置文件汇总

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    # 生产者确认
    publisher-confirm-type: correlated
    # 消费者配置
    listener:
      simple:
        acknowledge-mode: manual        # 手动确认
        prefetch: 1                     # 一次取一条
        default-requeue-rejected: false # 拒绝后不重回原队列
```

### 项目当前状态 vs 生产级

| 配置项 | 当前项目 | 生产级 |
|--------|---------|--------|
| 消息确认 | 自动（AUTO） | 手动（MANUAL） |
| 重试 | 无 | Spring Retry 3 次 |
| 死信队列 | 无 | 有（DLQ） |
| Publisher Confirm | 无 | 有 |
| 持久化 | 默认 | durable=true |
| prefetch | 默认 | 1 |

---

## 十三、RabbitMQ vs 其他 MQ

| 特性 | RabbitMQ | Kafka | RocketMQ |
|------|----------|-------|----------|
| 协议 | AMQP | 自定义 | 自定义 |
| 吞吐量 | 万级/秒 | 百万级/秒 | 十万级/秒 |
| 延迟 | 微秒级 | 毫秒级 | 毫秒级 |
| 消息可靠性 | 高 | 高 | 高 |
| 适用场景 | 业务消息、任务队列 | 日志、大数据流 | 电商、金融 |
| 学习成本 | 低 | 中 | 中 |

**面试话术**："项目用 RabbitMQ 是因为业务消息量不大（万级/秒），需要的是低延迟和高可靠性，RabbitMQ 完全够用，而且 Spring Boot 整合最简单。"

---

## 十四、常见面试问题

### Q1：RabbitMQ 怎么保证消息不丢？

```
三个环节都要保证：
  1. 生产者 → MQ：开启 Publisher Confirm
  2. MQ 自身：队列和消息都持久化（durable=true）
  3. MQ → 消费者：手动 Ack，处理完再确认
```

### Q2：消费者挂了，消息怎么办？

```
默认：消息重回队列，等其他消费者来取
如果配置了死信队列：多次失败后进死信队列，人工处理
```

### Q3：怎么避免消息重复消费（幂等性）？

```
方案：消费前先查 DB，看这条消息有没有处理过
  → 用 traceId 或消息 ID 做唯一键
  → 处理过就跳过
```

### Q4：消息积压怎么办？

```
1. 紧急扩容消费者（加机器）
2. 临时把消息转到更大的队列
3. 消费者批量处理（减少 DB 交互次数）
```

---

## 十五、30 秒面试话术

> "项目用 RabbitMQ 做异步审计和交易事件通知。用户聊天后，主流程直接返回响应，同时往 MQ 发一条审计事件。消费者在后台监听队列，收到消息后异步写入 MySQL。好处是：审计失败不影响用户体验，高并发时队列天然削峰，生产者和消费者完全解耦。消息序列化用 JSON，支持跨语言消费。本地开发没 MQ 时优雅降级，不阻塞主流程。"

---

## 十六、课后练习

**目标**：在项目中给"用户登录"事件加一个 MQ 通知。

**步骤**：
1. 在 `RabbitConfig` 中新增一个登录事件的 Exchange、Queue、Binding
2. 创建 `LoginEvent`（record 类型）
3. 创建 `LoginEventProducer`，在登录成功后发消息
4. 创建 `LoginEventConsumer`，消费消息后记录登录日志到 DB
5. 启动项目，登录后观察控制台日志

**验收标准**：登录成功后，Consumer 控制台打印出"收到登录事件"日志。

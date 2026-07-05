# Engineering Notes

## 已实现的小而值钱的工程点

### 1. 一键启动脚本

- 根目录 `start_all.ps1` 会先拉起中间件，再启动 Python、Java 和前端。
- 启动后自动做健康检查并输出访问地址。
- 对 Windows 本地开发比较友好，降低演示成本。

### 2. SSE 前端封装

- 前端没有直接依赖 `EventSource`。
- 使用 `fetch + ReadableStream` 手动解析 `data:` 行，支持自定义 Header。
- 这比“能跑就行”的实现更稳，也更容易和登录态结合。

### 3. Sentinel 限流降级返回

- Sentinel 拦截后统一返回 `429 + JSON`。
- 前端可以直接按业务错误处理，不会拿到默认 HTML 错页。
- 适合保护高频行情接口、下单接口和 AI 流式接口。

### 4. Redis key 命名规范

当前已集中管理的 key：

- `market:quote:{symbol}`
- `email:code:{scene}:{email}`
- `email:cooldown:{scene}:{email}`
- `paper:account:lock:{accountId}`
- `auth:token:{token}`

推荐 TTL 策略：

- 行情缓存：`30s`
- 邮箱验证码：`300s`
- 邮件重发冷却：`60s`
- 下单锁：`10s`
- 登录态 token：`30d`

### 4.1 Spring Security 登录态方案

- Java 网关使用 `Spring Security` 作为认证框架，不再依赖 `Sa-Token`。
- 登录成功后签发随机 `Bearer Token`，前端统一放入 `Authorization: Bearer <token>`。
- Redis 只保存 `token -> userId`，网关在过滤器里解析 token，再装配到 `SecurityContext`。
- Controller 层继续保留 `@LoginRequired / @RequireAdmin`，这样迁移成本低，接口语义也清晰。

### 5. traceId 检索思路

同一条 AI 请求应尽量用同一个 `traceId` 串起来，便于排查：

- Java 网关日志
- 聊天历史表
- 人工工单表
- 异步审计日志
- Python AI 日志 / Langfuse

示例：

```powershell
Get-Content .\logs\java.out.log | Select-String "traceId=YOUR_TRACE_ID"
```

### 6. 压测脚本

- 根目录 `stress_test.py` 可对登录态接口、行情、自选、模拟交易、AI 会话列表等做简单并发压测。
- 适合补充一页“不是没测过”的工程证据。

运行方式：

```powershell
python .\stress_test.py
```

## 适合继续补，但不要做重的点

### RabbitMQ 重试 / 死信

- 适合补成文档和简单配置。
- 面试里可以讲“主链路不重试阻塞，失败消息进入死信队列再人工排查”。

### 统一日志字段

- 固定输出 `traceId`, `userId`, `sessionId`, `role`, `costMs`。
- 这是比“再加一个中间件”更值钱的工程味。

### 简单压测结果沉淀

- 选 3 到 5 个核心接口。
- 放一张表：并发、成功率、平均响应时间、QPS。
- 比只放脚本更像工程项目。

## 不建议补的东西

- 主从、集群、分库分表
- K8s、服务网格
- 复杂分布式事务

对于实习面试，这些很容易被追问到答不住，收益不如“小而真”的工程细节。

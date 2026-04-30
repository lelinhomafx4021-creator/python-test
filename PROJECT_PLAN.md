# AI 投顾会员终端一期计划书

## 一期目标

把当前仓库从“AI 问答演示项目”升级为“可持续扩展的投顾会员终端”。

一期主打四个能力面：

- 会员身份与配额
- 行情与自选
- AI 投研副驾
- 模拟交易

同时保留人工兜底能力，形成 `AI -> 转人工 -> 工单可见` 的业务闭环。

## 业务域拆分

Java 主工程按模块化单体推进，当前规划为：

- `identity`
- `membership`
- `market`
- `watchlist`
- `papertrading`
- `ai`
- `ops`
- `shared`

Python 继续作为 AI 侧车存在，专注：

- 流式问答
- 投研解释
- 标题摘要
- 行情适配

## 一期主业务模型

### 用户与会员

- `users`
- `user_profiles`
- `membership_plans`
- `user_memberships`
- `user_feature_quotas`

### 行情与自选

- `stocks`
- `sectors`
- `market_quotes`
- `watchlists`
- `watchlist_items`

### 模拟交易

- `paper_accounts`
- `paper_positions`
- `paper_orders`
- `paper_trades`
- `paper_daily_assets`

### AI 与运营

- `ai_chat_turns`
- `ai_chat_audit`
- `ai_handoff_tickets`
- `ai_sessions`
- `ai_usage_records`
- `system_configs`

## 一期接口规划

### 用户与会员

- `GET /api/v1/users/me`
- `GET /api/v1/memberships/me`
- `GET /api/v1/quotas/me`

### 行情与自选

- `GET /api/v1/market/quotes`
- `GET /api/v1/sectors`
- `GET /api/v1/watchlists`
- `POST /api/v1/watchlists`
- `POST /api/v1/watchlists/{id}/items`
- `DELETE /api/v1/watchlists/{id}/items/{itemId}`

### 模拟交易

- `GET /api/v1/paper/accounts/me`
- `GET /api/v1/paper/accounts/{id}/positions`
- `GET /api/v1/paper/accounts/{id}/orders`
- `POST /api/v1/paper/orders`
- `POST /api/v1/paper/orders/{id}/cancel`

### AI 副驾

- `POST /api/v1/ai/chat`
- `POST /api/v1/ai/chat/stream`
- `GET /api/v1/ai/handoff-tickets`

## 基础设施口径

- `MySQL`：业务真相
- `Redis`：缓存、登录态、锁、配额
- `RabbitMQ`：异步任务与审计
- `Postgres + pgvector`：向量检索
- `Langfuse`：观测
- `Sentinel`：流控与降级

## 一期原则

- 不做真实券商接入
- 不做复杂撮合引擎
- 不做完整 CMS / 后台运营平台
- 先把“可演示、可扩展、可继续开发”的主链路做扎实

## 后续二期建议

- 接入更完整的会员权益后台
- 增加提醒中心与异步任务编排
- 完善 AI 使用量计费与统计
- 引入更细的风控规则与人工处理台
- 逐步拆出行情采集服务与策略服务

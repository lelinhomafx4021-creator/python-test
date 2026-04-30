# AI Investor

## 项目定位

`AI Investor` 已从最初的 AI 问答网关，升级为“AI 投顾会员终端一期”骨架。

当前仓库的目标不再只是回答问题，而是支持一个更完整的投顾类产品工作台：

- 会员身份与配额
- 行情与板块查询
- 自选股分组管理
- AI 投研副驾
- 模拟交易
- 人工兜底工单闭环

## 技术架构

### Java 主业务服务

Java 侧负责主业务真相与对外 API，当前已经拆出以下业务域：

- `identity`
- `membership`
- `market`
- `watchlist`
- `papertrading`
- `ai`
- `ops`
- `shared`

### Python AI 侧车

Python 侧继续负责：

- AI 问答与 SSE 流式输出
- 标题生成
- 行情内部适配
- RAG / LangGraph 相关能力

### 基础设施

- `MySQL`：业务真相存储
- `Redis`：登录态、热点行情、分布式锁、配额计数
- `RabbitMQ`：异步审计与后续异步任务
- `Postgres + pgvector`：向量检索
- `Langfuse`：LLMOps 可观测
- `Sentinel`：热点接口限流与降级保护

## 一键启动

优先使用根目录 PowerShell 启动脚本：

```powershell
.\start_all.ps1
```

脚本会自动完成：

- 检查 `docker`、`java`、`node`、`npm`、`mvn`、Python 虚拟环境
- 启动 `docker compose` 中间件
- 启动 Python AI、Java Gateway、Frontend
- 轮询健康检查
- 输出访问地址、演示账号和日志目录

### 启动后可访问地址

- 前端工作台：`http://127.0.0.1:5173`
- Java 网关：`http://127.0.0.1:8080`
- Python AI：`http://127.0.0.1:8000`
- Langfuse：`http://127.0.0.1:3000`
- Sentinel 控制台：`http://127.0.0.1:8858`

### 默认演示账号

- 用户名：`admin`
- 密码：`123456`

## 手动启动

### 1. 启动中间件

```powershell
docker compose up -d
```

### 2. Python AI

```powershell
cd aipy2
.venv\Scripts\python.exe main.py
```

### 3. Java Gateway

```powershell
cd java-ai-gateway
mvn spring-boot:run
```

### 4. Frontend

```powershell
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

## 一期核心接口

### 身份与会员

- `GET /api/v1/users/me`
- `GET /api/v1/memberships/me`
- `GET /api/v1/quotas/me`

### 行情与自选

- `GET /api/v1/market/quotes?symbols=600519,000001`
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

- 兼容旧接口：`/gateway/ai/*`
- 新标准接口：`/api/v1/ai/chat`
- 新标准接口：`/api/v1/ai/chat/stream`
- 新标准接口：`/api/v1/ai/handoff-tickets`

## 演示路径

### AI 转人工闭环

1. 使用 `admin / 123456` 登录。
2. 发起普通投研问题，观察 SSE 流式回答。
3. 再输入“转人工”或持续触发高风险/不稳定问题。
4. 打开人工工单面板，查看兜底工单。
5. 点击工单返回原始会话，演示闭环。

### 会员终端一期

1. 登录后查看当前用户资料与会员信息。
2. 查询股票行情与板块。
3. 创建自选分组并添加股票。
4. 初始化模拟账户并尝试下单。
5. 结合 AI 问答和人工工单展示完整工作台故事线。

## 开发说明

- 新增代码中的类注释、方法注释、字段注释、脚本注释统一使用中文。
- 一期交易模型采用“按最近行情快照成交”的轻量模拟，不接真实券商。
- AI 会话与主业务状态分离，会员、自选、交易等主状态仍由 Java 维护。

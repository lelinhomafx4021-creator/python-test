# Demo Runbook

## 目标

这份手册是给面试演示用的最短路径，不讲大而空的架构图，直接讲清楚：

- 这是一个有完整业务链路的 AI 应用，不是单纯接模型接口
- Java 和 Python 各自承担清晰职责
- 有基础工程化能力，不是只会把页面跑起来

## 演示前 2 分钟准备

1. 启动服务：

```powershell
.\start_all.ps1
```

2. 重置演示数据：

```powershell
cd .\aipy2
.\.venv\Scripts\python.exe -m scripts.seed_demo_data
cd ..
```

3. 跑一轮冒烟检查：

```powershell
python .\demo_smoke_test.py
```

4. 如需展示压测结果：

```powershell
python .\stress_test.py --concurrency 10 --requests 20 --markdown-out docs/PRESSURE_TEST_RESULT.md
```

## 演示账号

- 管理员：`admin / 123456`
- 普通演示用户：`investor_zhang / 123456`
- 其他样例用户：`investor_li`、`investor_wang`、`investor_chen`、`investor_sun`

## 5 分钟演示顺序

1. 先讲项目定位
   这不是聊天 Demo，而是一个面向投资场景的 AI 工作台，业务链路覆盖账号、会员、自选、模拟交易、AI 投研和人工兜底。

2. 登录前端工作台
   地址：`http://127.0.0.1:5173`
   用 `investor_zhang / 123456` 登录，先展示首页、自选股、模拟账户和通知，证明不是空页面。

3. 进入 AI 会话
   强调这里不是前端直连大模型，而是先走 Java 网关，再转到 Python AI 服务。
   可以重点讲三件事：SSE 流式返回、会话持久化、traceId 留痕。

4. 展示管理端
   用 `admin / 123456` 登录后看总览，说明系统里有用户、会员、AI 会话、工单和交易演示数据。

5. 讲人工兜底
   当 AI 不确定或用户要求更具体建议时，会落人工工单，而不是硬编答案。
   这是比“接了个模型 API”更值钱的业务闭环。

## 你可以怎么讲架构

- `frontend/`
  Vue 3 工作台，负责交互和 SSE 消费。

- `java-ai-gateway/`
  Spring Boot 业务网关，负责登录鉴权、会员状态、交易、自选、公告、通知、AI 历史、人工工单和审计。

- `aipy2/`
  FastAPI + LangGraph AI 服务，负责 AI 工作流编排、RAG、工具调用、流式输出和观测。

## 面试时重点讲的小而值钱的点

- Redis 不只是“用了缓存”，而是有明确 key 命名和 TTL 策略
- Sentinel 不只是“接了限流”，而是统一返回 JSON，前端能稳定处理
- AI 链路不是一次性返回，而是支持流式输出、会话留痕和人工兜底
- 有种子数据、冒烟脚本和压测脚本，说明这个项目可重复演示

## 不建议主动吹的点

- 不要主动吹集群、主从、分布式事务
- 不要把 RabbitMQ、Redis、Postgres 讲成“为了堆技术”
- 不要说自己做了生产级高可用，除非你真的实现并验证过

## 如果面试官追问“为什么 Java + Python”

- Java 更适合承接稳定的业务网关和权限、交易、会员这些规则型逻辑
- Python 更适合承接 AI 编排、RAG 和模型侧快速试验
- 这样拆分后，业务稳定性和 AI 迭代速度都更容易兼顾

## 如果现场出问题

- 先执行 `python .\demo_smoke_test.py`
- 如果只是数据空了，执行 `aipy2/scripts/seed_demo_data.py`
- 如果只是中文在终端乱码，优先判断终端编码，不要先怀疑 MySQL 数据损坏

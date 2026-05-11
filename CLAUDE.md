# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 用户身份与协作方式

- 用户是学生/学习者，正在通过这个项目学习技术
- **教学优先于执行**：每一步都要解释原理和为什么，不要只给结果
- 遇到新概念、新技术、新工具时，主动科普背景知识
- 有多种实现方式时，简要说明各自的优缺点和适用场景
- 代码注释可以省略，但口头讲解不能省

## 项目概述

面向投顾会员场景的 AI 原生投资工作台，把会员体系、行情查询、自选股、AI 投研副驾、模拟交易、AI 转人工闭环串起来的产品骨架。

## 常用命令

### 一键启动（推荐）

```powershell
.\start_all.ps1
```

脚本自动完成：检查依赖 → 启动 Docker 中间件 → 启动三个服务 → 健康检查 → 输出访问地址。

### 分模块启动

**中间件（先行）：**
```bash
docker compose up -d
```

**Python AI 服务（端口 8000）：**
```bash
cd aipy2
python main.py
```

**Java 网关（端口 8080）：**
```bash
cd java-ai-gateway
mvn spring-boot:run
```

**前端（端口 5173）：**
```bash
cd frontend
npm install
npm run dev
```

### 构建

```bash
# 前端类型检查 + 构建
cd frontend && npm run build

# Java 编译
cd java-ai-gateway && mvn compile

# Python 无编译步骤，安装依赖用 pip install -e aipy2/
```

### 测试

```bash
# 前端（如有）
cd frontend && npm run test

# Java
cd java-ai-gateway && mvn test

# Python（如有）
cd aipy2 && pytest
```

### 中间件

| 服务 | 端口 | 用途 |
|------|------|------|
| MySQL | 3306 | 业务数据（用户、会员、自选、订单） |
| Redis | 6379 | 缓存 / Session |
| RabbitMQ | 5672 | 异步消息（AI 审计、工单等） |
| Postgres + pgvector | 5432 | AI 状态持久化、向量存储 |
| Langfuse | 3000 | LLM 调用链追踪 |
| Sentinel | 8858 | 流量控制控制台 |

## 架构

采用 **Java + Python 双后端**：

```
Vue 3 Frontend (5173)
  → Java Gateway (8080) — 主业务真相：身份/会员/行情/自选/模拟交易/AI会话/人工工单
    → Python AI (8000) — 模型调用、SSE 流式输出、LangGraph、RAG
```

- **Java 管业务**：高并发 IO、数据库审计、消息队列、对外 API
- **Python 管 AI**：Agent 编排、流式问答、RAG、LangGraph 状态图
- **关键设计**：AI 问答通过 Java 透传到 Python，SSE 流式输出原路返回前端；LangGraph checkpoint 持久化到 Postgres

## 项目结构

```
ai-investor/
├─ aipy2/                 # Python AI 服务 (FastAPI + LangGraph)
│  └─ main.py             # 入口
├─ frontend/              # Vue 3 + TypeScript + Tailwind + ECharts
├─ java-ai-gateway/       # Java 主业务 (Spring Boot 3.x + MyBatis-Plus)
├─ data/                  # 本地数据与中间件挂载
├─ logs/                  # 启动日志
├─ docker-compose.yml     # 中间件编排
├─ start_all.ps1          # 一键启动脚本
├─ README.md              # 项目介绍与演示路线
├─ ARCHITECTURE.md        # 架构深度说明
└─ PROJECT_PLAN.md        # 一期规划
```

## 演示账号

- 用户名：`admin`，密码：`123456`

## 演示路线

**AI 投研 + 转人工闭环：** 登录 → AI 会话页发起投研问题（观察 SSE 流式） → 输入"转人工"触发升级 → 人工工单面板查看 → 工单返回原会话

**会员工作台：** 登录 → 查看会员信息 → 查行情 → 创建自选股分组 → 初始化模拟账户 → 下单

# AI Investor 项目书（学习版 / 企业分工版）

> 适用人群：初学者（你）
>
> 目标：先把“真正的大模型应用”搭出来，再自顶向下学习每一层。

---

## 1. 项目目标（先讲人话）

我们要做的不是一个“聊天玩具”，而是一个**企业可落地**的 AI 投研系统：

- 前台（前端）只调用 **Java 网关**。
- Java 网关负责业务能力（用户、会话、权限、审计、限流、MQ）。
- Python 服务只做 AI 能力（多智能体、RAG、工具调用、流式输出）。

这就是国内大项目常见分工：

- **Java = 主业务系统（中台）**
- **Python = AI 引擎系统（能力服务）**

---

## 2. 分工边界（重点，必须背下来）

### 2.1 Java 负责什么（业务面）

1. 登录态、鉴权、RBAC（谁能调什么）
2. session 管理（会话创建、会话列表、历史记录）
3. Chat 历史存储（MyBatis-Plus）
4. Sentinel 限流/熔断
5. MQ 审计事件（RocketMQ）
6. 对前端输出统一 API 协议

### 2.2 Python 负责什么（AI 面）

1. 多智能体图编排（LangGraph）
2. RAG 检索（本地 + 联网）
3. 检索纠错节点（query rewrite）
4. 回答评审打回节点（critic）
5. SSE 流式输出
6. 过程 trace / A2A 消息（用于解释和调试）

### 2.3 强规则（避免职责混乱）

- Python 不做用户体系，不做业务 session 主数据。
- Java 不做模型推理，不做 Agent 编排。

---

## 3. 当前项目结构（学习视角）

```text
ai-investor/
  aipy2/                    # Python AI 服务
    app/
      api/healthy/chat.py   # AI 同步/流式接口
      core/multi_graph_agent.py  # 多智能体图核心
      tools/                # 检索和行情工具
      services/             # （后续会逐步只保留 AI 内部服务）
      schemas/              # 请求/响应模型
    main.py
    pyproject.toml

  java-ai-gateway/          # Java 网关（业务主系统）
    src/main/java/.../config
    src/main/java/.../controller
    src/main/java/.../service
```

---

## 4. 核心业务流程（从请求到回答）

### 4.1 同步流程

1. 前端请求 Java：`/gateway/ai/chat`
2. Java 完成鉴权与会话逻辑
3. Java 调 Python：`/ai/chat`
4. Python 跑多智能体图得到结果
5. Java 记录历史 + 发 MQ 审计
6. Java 返回前端统一响应

### 4.2 流式流程（SSE）

1. 前端订阅 Java 的流式接口
2. Java 转发 Python SSE
3. Python 按阶段输出事件（accepted/review/final/done）
4. Java 可同时打审计日志

---

## 5. Python 多智能体图（你最该学的）

当前图是“企业常见可解释链路”：

1. `plan_node`：先判断意图（股票/知识）
2. `stock_tool_node` 或 `retrieve_node`：执行工具
3. `retrieval_guard_node`：检索质量差则改写并重检索
4. `answer_node`：分析生成答案
5. `review_node`：检查结构，不合格打回

学习重点不是“记函数名”，而是理解：

- 为什么要有状态（AgentState）
- 为什么要有 guard（降级与纠错）
- 为什么要有 critic（质量闭环）

---

## 6. 接口契约（Java 调 Python）

### 6.1 Python 同步接口

- Method: `GET /ai/chat`（后续升级为 POST）
- Params:
  - `message`: 用户问题
  - `session_id`: Java 传入的会话ID
- Header:
  - `X-User-Id`: Java 传入用户ID

### 6.2 Python 流式接口

- Method: `GET /ai/chat/stream`
- 同步接口同样参数
- 返回 `text/event-stream`

> 说明：`thread_id` 在 Python 侧根据 `user_id:session_id` 组装，仅用于 AI 线程上下文，不承担业务主数据职责。

---

## 7. 快速启动（先跑起来）

> 下面是最小跑通步骤。你后面学习时，始终遵循“先跑通，再优化”。

### 7.1 启动 Python AI 服务

```bash
cd aipy2
uv run python main.py
```

### 7.2 启动 Java 网关

```bash
cd java-ai-gateway
mvn spring-boot:run
```

### 7.3 验证联调

```bash
# 调 Java 网关（推荐），让 Java 负责业务层
curl -H "X-User-Id: u1001" "http://127.0.0.1:8080/gateway/ai/chat?message=600519今天怎么样&session_id=s1"
```

---

## 8. 学习路线（自顶向下）

### 第 1 周：架构认知

- 目标：讲清楚“为什么 Java 管业务、Python 管 AI”。
- 验收：你能口述整个请求链路。

### 第 2 周：多智能体与状态

- 目标：看懂 AgentState、节点路由、打回机制。
- 验收：你能画出图并解释每个节点职责。

### 第 3 周：RAG 与工具

- 目标：理解本地检索、联网检索、自动降级。
- 验收：你能解释“检索失败为什么还可回答”。

### 第 4 周：企业治理

- 目标：理解 Sentinel/MQ/审计/追踪如何接入。
- 验收：你能从网关日志追踪到一次完整请求。

---

## 9. 代码注释规范（初学者版）

以后我们所有关键代码都按这个注释标准写：

1. **这个类/函数是干嘛的**（业务目的）
2. **输入是什么**（参数含义）
3. **输出是什么**（返回结构）
4. **失败怎么处理**（降级策略）
5. **为什么这样设计**（trade-off）

---

## 10. 下一步执行单（直接开工）

1. 把 Python `/ai/chat` 从 GET 升级到 POST（更标准）
2. Java 网关也改 POST，统一 DTO
3. Java 引入 MyBatis-Plus，承接 session/history 主数据
4. Python 删除业务历史接口，只保留 AI 能力接口
5. 补一套联调测试清单（同步+流式+降级）

---

## 11. 你现在应该怎么用这份项目书

你每次学习前看三件事：

- 本次要学哪一层（架构 / agent / rag / 治理）
- 本次验收是什么（能说清 / 能跑通 / 能调试）
- 本次只改哪些文件（避免散改）

这样你学习速度会非常快，而且不会迷路。

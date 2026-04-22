# AI Investor 智能投研助手 · 项目规划书

> 定位：校招面试级全栈 AI Agent 项目，展示 LLM 应用工程化能力

---

## 一、项目概述

AI Investor 是一款基于 **LangGraph + FastAPI + Vue 3** 的智能投研助手。
核心能力：用户提出投研问题 → AI 自动检索多路数据源 → 生成报告 → 专家评审 → 自动纠错。

### 技术亮点（面试专用）
| 模块 | 技术方案 | 面试谈资 |
|------|---------|---------|
| Agent 架构 | LangGraph StateGraph + 条件边 | Self-RAG 闭环纠错 |
| 检索引擎 | Vector + BM25 混合检索 | RRF 融合算法 |
| 流式输出 | FastAPI SSE + Vue EventSource | 全异步生成器 |
| 前端交互 | Gemini 风格居中式 UI | 思考过程可视化 |
| 安全防护 | CSS user-select + JS 事件拦截 | 投研内容保护 |

---

## 二、系统架构

```
用户浏览器 (Vue 3 / Vite)
    │  SSE 流式
    ▼
Java 网关 (Spring Boot WebFlux)
    │  鉴权 / 限流 / 审计
    ▼
Python AI 服务 (FastAPI async)
    │
    ├── LangGraph 工作流
    │     ├── rewrite_node  (问题重写)
    │     ├── search_node   (混合检索)
    │     ├── answer_node   (报告生成)
    │     └── critic_node   (专家评审) ──┐
    │            ▲                      │
    │            └──── 打回重做 ─────────┘
    │
    ├── RAG 引擎
    │     ├── Vector Store (语义检索)
    │     ├── BM25 Scorer  (关键词检索)
    │     ├── RRF Fusion   (结果融合)
    │     └── Rerank        (精选重排)
    │
    └── 数据入库
          ├── PDF 解析
          └── Semantic Chunking (语义切分)
```

---

## 三、目录结构

```
ai-investor/
├── frontend/                # Vue 3 + Vite 前端
│   └── src/
│       ├── App.vue          # Gemini 风格主页面
│       └── style.css        # 全局样式 + 防复制
│
├── aipy2/                   # Python AI 核心服务
│   ├── main.py              # FastAPI 启动入口
│   ├── app/
│   │   ├── core/
│   │   │   ├── config.py          # 配置管理
│   │   │   ├── llm.py             # LLM 初始化
│   │   │   └── multi_graph_agent.py  # ★ LangGraph 工作流
│   │   ├── api/healthy/
│   │   │   └── chat.py            # SSE 流式接口
│   │   ├── tools/
│   │   │   └── retriever_tool.py  # ★ 混合检索引擎
│   │   └── rag/
│   │       └── vector_store.py    # 向量库封装
│   └── scripts/
│       └── ingest_docs.py         # 语义切分入库脚本
│
└── gateway/                 # Java Spring Boot 网关
    └── (鉴权/限流/审计)
```

---

## 四、核心模块说明

### 4.1 LangGraph 自反思工作流 (`multi_graph_agent.py`)
- **AgentState**: 包含 `messages`(消息流)、`queries`(子查询)、`knowledge`(检索知识)、`retry_count`(重试计数)、`review_status`(评审结论)
- **节点链路**: `rewrite → search → answer → critic → (pass→END / fail→rewrite)`
- **防死循环**: `retry_count >= 3` 时强制通过

### 4.2 混合检索引擎 (`retriever_tool.py`)
- **三路并发**: Vector + BM25 + Web 同时执行
- **RRF 融合**: `Score = Σ 1/(k + rank)`，k=60
- **BM25 公式**: `Score = Σ IDF × (TF×(k1+1)) / (TF + k1×(1-b+b×L/avgL))`

### 4.3 Gemini 风格前端 (`App.vue`)
- **双视图模式**: Splash(居中搜索) ↔ Chat(对话流) 平滑切换
- **思考过程**: 可折叠的 Thought Process 面板，展示 LangGraph 节点状态
- **SSE 解析**: JSON 格式解析 `{ stage, data }` 结构化事件

---

## 五、环境配置

### 依赖
```
# Python
fastapi, uvicorn, langchain, langgraph, httpx, pydantic

# Frontend
vue@3, vite, markdown-it, lucide-vue-next
```

### 环境变量 (.env)
```
DASH_API_KEY=xxx          # 通义千问 API
SERPER_API_KEY=xxx        # 联网搜索
DATABASE_URL=postgresql+psycopg://...
```

### 启动命令
```bash
# 后端
cd aipy2 && python -m uvicorn main:app --port 8000

# 前端
cd frontend && npm run dev
```

---

## 六、已完成 & 待办

### ✅ 已完成
- [x] 全异步 FastAPI + LangGraph 架构
- [x] Self-RAG 闭环 (Critic Node + Conditional Edge)
- [x] BM25 + Vector 混合检索 + RRF 融合
- [x] Rerank 重排序
- [x] 语义切分 (Semantic Chunking)
- [x] Gemini 风格 UI + 思考过程可视化
- [x] 内容安全防护 (防复制粘贴)
- [x] SSE 流式输出全链路打通

### 🔲 后续可拓展
- [ ] 接入真实 PDF 解析库 (PyMuPDF)
- [ ] 接入真实 Rerank 模型 (如 bge-reranker)
- [ ] 增加多轮对话记忆 (LangGraph Checkpointer)
- [ ] Java 网关完整鉴权链路
- [ ] Docker Compose 一键部署
- [ ] 前端暗色/亮色主题切换

---

> 📌 本文档是项目的核心参考，新会话中可直接阅读此文件恢复上下文。

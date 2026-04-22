# 教程 06：SSE 事件流 —— 前端收到的每一条 JSON 长什么样？

## 一句话概念
当用户发一个问题后，后端不是一次性返回答案，而是**一条一条地推送事件**（像微信打字一样）。每条事件都是一个 JSON，前端根据 `stage` 字段来决定"现在该显示什么"。

---

## 1. 完整的事件推送时间线

假设用户问了 **"茅台最近的财报表现怎么样？"**，后端会依次推送以下 JSON：

### 事件 1：`accepted`（收到请求）
```json
{
  "stage": "accepted",
  "data": {
    "query": "茅台最近的财报表现怎么样？"
  }
}
```
- **含义**：后端确认收到了用户的问题
- **前端动作**：显示 "正在思考..." 的加载动画

### 事件 2：[rewrite](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#33-62)（问题拆解完成）
```json
{
  "stage": "rewrite",
  "data": {
    "step": "💡 正在重新校准搜索意图 (消耗: 156 tokens)..."
  }
}
```
- **含义**：AI 已经把问题拆解成搜索关键词了
- **前端动作**：在"思考过程"区域显示这条步骤

### 事件 3：[search](file:///d:/ai-investor/aipy2/app/tools/retriever_tool.py#39-54)（检索完成）
```json
{
  "stage": "search",
  "data": {
    "step": "🔍 多路并行检索中（Vector + BM25 + Web）..."
  }
}
```

### 事件 4：[answer](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#73-93)（草稿撰写完成）
```json
{
  "stage": "answer",
  "data": {
    "step": "✍️ 分析师正在撰写深度报告..."
  }
}
```

### 事件 5：[critic](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#94-138)（评审完成）

**场景 A：评审通过 ✅**
```json
{
  "stage": "critic",
  "data": {
    "step": "👸 评审通过，内容可信"
  }
}
```

**场景 B：评审不通过 ❌（会触发重试循环）**
```json
{
  "stage": "critic",
  "data": {
    "step": "🕵️ 发现缺陷：缺少具体的净利润数字，已打回重写..."
  }
}
```
→ 此时流程会**跳回 rewrite**，你会再次收到事件 2、3、4、5

### 事件 6：`final_answer`（最终答案）
```json
{
  "stage": "final_answer",
  "data": {
    "answer": "根据财务手册数据，贵州茅台2024年一季度实现净利润同比增长15.7%，超出市场预期...",
    "source": "AI 投研闭环引擎 (Self-RAG v2)",
    "usage": 2340
  }
}
```
- **[answer](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#73-93)**：最终的投研报告正文
- **[source](file:///d:/ai-investor/aipy2/app/core/llm.py#86-105)**：答案来源标识
- **`usage`**：整个流程消耗的 Token 总量
- **前端动作**：把 [answer](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#73-93) 渲染成 Markdown 展示给用户

### 事件 7：`done`（流程结束）
```json
{
  "stage": "done",
  "data": {
    "status": "success"
  }
}
```
- **前端动作**：关闭加载动画，启用输入框

---

## 2. 如果中间出错了？

```json
{
  "stage": "error",
  "data": {
    "msg": "LLM API 超时，请稍后重试"
  }
}
```
- **前端动作**：显示错误提示弹窗

---

## 3. 前端怎么消费这些 JSON？

在 [App.vue](file:///d:/ai-investor/frontend/src/App.vue) 中，前端用 `EventSource` 或 `fetch` 读取 SSE：

```javascript
// 简化版逻辑
const response = await fetch('/ai/v1/chat/stream', { method: 'POST', body: ... });
const reader = response.body.getReader();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const text = new TextDecoder().decode(value);
  // text = "event: message\ndata: {\"stage\":\"rewrite\",\"data\":{...}}\n\n"
  
  const json = JSON.parse(text.split("data: ")[1]);
  
  switch (json.stage) {
    case "accepted":    showLoading();           break;
    case "rewrite":
    case "search":
    case "answer":
    case "critic":      showThought(json.data.step); break;  // 渲染思考步骤
    case "final_answer": renderMarkdown(json.data.answer); break;  // 渲染最终答案
    case "done":        hideLoading();           break;
    case "error":       showError(json.data.msg); break;
  }
}
```

---

## 4. 一次完整对话的事件流图

```
时间轴 →

前端请求 ─────────────────────────────────────────────────────→
后端推送  [accepted] [rewrite] [search] [answer] [critic:fail]
                                                       ↓ 重试
          [rewrite] [search] [answer] [critic:pass] [final_answer] [done]
```

> **面试点**：这整套事件结构就是"Agent 透明化（Agent Transparency）"的实现。用户不再面对一个"黑盒子"，而是**能看到 AI 在想什么、搜什么、写什么**，大幅提升用户信任度。


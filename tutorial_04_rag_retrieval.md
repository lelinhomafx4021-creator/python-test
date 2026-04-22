# 教程 04：RAG 检索 —— AI 不再"胡说八道"的秘密

## 一句话概念
RAG（Retrieval-Augmented Generation）= **先搜资料，再让 AI 基于资料回答**。这解决了大模型"一本正经胡说八道"的致命缺陷。

---

## 1. 为什么需要 RAG？

```
❌ 没有 RAG 的 AI：
用户: "茅台2024年Q1净利润？"
AI: "茅台2024年Q1净利润约为230亿元"  ← 编的！没有数据来源！

✅ 有 RAG 的 AI：
1. 先搜索 → 找到："{财务手册}: 贵州茅台2024年一季度实现净利润同比增长15.7%"
2. 再回答 → "根据财务手册数据，茅台2024年Q1净利润同比增长15.7%"
```

---

## 2. 我们项目的三路检索

打开 [retriever_tool.py](file:///d:/ai-investor/aipy2/app/tools/retriever_tool.py)，我们用了三条"搜索管道"并行工作：

| 路径 | 技术 | 擅长什么 | 文件中的函数 |
|------|------|----------|------------|
| 🧠 语义路 | 向量搜索 (pgvector) | 理解"意思相近"的内容 | [_search_local_async](file:///d:/ai-investor/aipy2/app/tools/retriever_tool.py#73-79) |
| 📝 关键词路 | BM25 (jieba分词) | 精准匹配专业术语/数字 | [_search_bm25_async](file:///d:/ai-investor/aipy2/app/tools/retriever_tool.py#64-69) |
| 🌐 联网路 | Serper API | 搜索最新互联网信息 | [_search_web_async](file:///d:/ai-investor/aipy2/app/tools/retriever_tool.py#80-93) |

### 为什么要三路？

```
用户问: "贵州茅台600519的PE是多少？"

🧠 语义路: 能找到"茅台估值相关"的段落（但可能找不到精确的PE数字）
📝 关键词路: 能精准匹配"600519"和"PE"这些关键词
🌐 联网路: 能搜到实时的市场数据

三路融合 → 互补短板 → 结果更全面
```

---

## 3. BM25 引擎详解

```python
class BM25Engine:
    def __init__(self, corpus_docs):
        # 1. 中文分词
        self.tokenized_corpus = [list(jieba.cut(doc["text"])) for doc in corpus_docs]
        # "茅台的毛利率保持在90%以上" → ["茅台", "的", "毛利率", "保持", "在", "90%", "以上"]
        
        # 2. 构建 BM25 索引
        self.bm25 = BM25Okapi(self.tokenized_corpus)
```

### BM25 算法核心思想（面试必考）

BM25 做的事情很简单：**给每个文档打分，看谁和查询词最相关**。

打分公式的核心因素：
- **TF（词频）**：查询词在文档中出现越多次，得分越高
- **IDF（逆文档频率）**：如果一个词在所有文档中都出现（如"的"），说明它不重要，得分低
- **文档长度惩罚**：太长的文档容易"碰运气"匹配到词，所以要打折

> **面试点**：BM25 是 Elasticsearch 的默认排序算法。你可以说"我在项目中使用了 BM25 进行关键词检索，并与向量搜索做了 RRF 融合"。

---

## 4. 向量搜索详解

```python
async def _search_local_async(query, top_k=3):
    results = await loop.run_in_executor(None, lambda: my_vector.search(query, top_k=top_k))
```

### 向量搜索的原理

```
1. 入库时：把文档用 Embedding 模型转成向量（一串数字）
   "茅台净利润增长15%" → [0.12, -0.34, 0.56, ..., 0.78]  (1536维)

2. 搜索时：把查询也转成向量
   "茅台赚了多少钱？" → [0.11, -0.33, 0.55, ..., 0.77]

3. 比较：计算两个向量的余弦相似度
   cos(query, doc) = 0.95  ← 非常相似！排第一
```

> **面试点**：向量搜索的优势是"语义理解"。即使用户问的是"茅台赚了多少钱"，它也能找到"茅台净利润增长"这种**意思相近但用词不同**的文档。

---

## 5. RRF 融合（把三路结果合并排序）

```python
async def _rrf_fusion(vector_results, bm25_results, top_k=3):
    k = 60  # 平滑参数
    scores = {}
    for rank, content in enumerate(vector_results):
        scores[content] = scores.get(content, 0) + 1.0 / (k + rank)
    for rank, doc in enumerate(bm25_results):
        scores[doc["content"]] = scores.get(doc["content"], 0) + 1.0 / (k + rank)
    # 按总分排序
    sorted_res = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [item[0] for item in sorted_res[:top_k]]
```

### RRF 是什么？

**RRF（Reciprocal Rank Fusion）**= 倒数排名融合。

```
向量搜索排名:  文档A(第1), 文档B(第2), 文档C(第3)
BM25搜索排名:  文档B(第1), 文档D(第2), 文档A(第3)

RRF 融合:
- 文档A: 1/(60+0) + 1/(60+2) = 0.0167 + 0.0161 = 0.0328  ← 综合第1
- 文档B: 1/(60+1) + 1/(60+0) = 0.0164 + 0.0167 = 0.0331  ← 综合并列
```

> **面试点**：RRF 的优势是"不需要归一化"。向量搜索的分数和 BM25 的分数量级完全不同，直接加没有意义。但用排名来融合，就避免了这个问题。

---

## 6. 完整的检索流程

```python
async def run_retrieval_async(queries, mode="auto", top_k=5):
    # 1. 三路并行搜索
    v_res, b_res, w_res = await asyncio.gather(
        _search_local_async(query),   # 向量
        _search_bm25_async(query),    # BM25
        _search_web_async(query)      # 联网
    )
    
    # 2. RRF 融合本地结果（向量 + BM25）
    fused_local = await _rrf_fusion(v_list, b_res)
    
    # 3. 合并联网结果
    all_candidates = fused_local + [w_res]
    
    # 4. Rerank 重排序
    return await _rerank_results(queries, all_candidates)
```

`asyncio.gather` 让三路搜索**同时启动**，而不是一个等一个，这就是异步编程的威力。


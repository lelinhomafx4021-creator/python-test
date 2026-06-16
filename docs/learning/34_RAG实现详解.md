# 34_RAG实现详解：pgvector + 裸 SQL + 阿里云 Embedding

> **核心目标**: 理解项目真实的 RAG 实现——不是 LangChain 封装，是手写 SQL
> **项目代码**: `aipy2/app/rag/vector_store.py` / `retriever_tool.py` / `chunker.py` / `parser.py`
> **面试价值**: pgvector 是 2026 年生产环境首选向量数据库方案

---

## 一、RAG 是什么

**RAG = Retrieval-Augmented Generation = 检索增强生成**

让 LLM 在回答前先查资料，基于资料生成回答。解决了两个核心问题：
1. LLM 知识截止日期问题（训练数据是旧的）
2. LLM 幻觉问题（有了资料约束，不容易编造）

---

## 二、项目整体检索架构

```
用户查询 "茅台股价多少"
    │
    ├── 1. rewrite_node 改写为搜索词
    │      ["贵州茅台 最新股价", "茅台 财报 2024", "贵州茅台 近期走势"]
    │
    ├── 2. search_node / fetch_data_node
    │      │
    │      ├── 本地向量检索 (pgvector)
    │      │      └── 查 doc_chunks 表，用 <=> 算余弦距离
    │      │
    │      ├── 联网检索 (Tavily)
    │      │      └── 走 Tavily Search API，topic=finance
    │      │
    │      └── 策略：先本地，没命中再联网 (auto 模式)
    │
    └── 3. answer_node 拿到 knowledge 文本，生成回答
```

---

## 三、为什么选 pgvector（不是 Milvus/Chroma）

```
选项            问题
Milvus          需要单独部署一个服务，运维成本高
Chroma          轻量但生产级特性不足
FAISS           内存库，重启丢数据
pgvector        和业务 PostgreSQL 共用，零额外运维
```

**面试话术**："选 pgvector 是因为项目已经用了 PostgreSQL 做业务存储和 LangGraph checkpointing，加一个 pgvector 扩展就能同时做向量检索，不需要引入新的基础设施。一个生产团队从专用向量库迁移到 pgvector 后，月成本从 $6000 降到 $700。"

---

## 四、VectorStore 类：psycopg2 + 手写 SQL

### 4.1 初始化

```python
# aipy2/app/rag/vector_store.py
import psycopg2
from pgvector.psycopg2 import register_vector

class VectorStore:
    def __init__(self, db_url, api_key, collection_name="doc_chunks",
                 embedding_model="text-embedding-v3"):
        self.api_key = api_key
        self.embedding_model = embedding_model
        self.api_url = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
        self.vector_size = 1024

        self.conn = psycopg2.connect(db_url)
        self.conn.autocommit = True
        with self.conn.cursor() as cur:
            cur.execute("CREATE EXTENSION IF NOT EXISTS vector")  # 自动装 pgvector
        register_vector(self.conn)  # 让 psycopg2 认识 VECTOR 类型
```

**关键**：不用 LangChain 的 `PGVector` wrapper——直接用 `psycopg2` + 手写 SQL。为什么？因为需要完全控制 HNSW 索引参数和 `<=>` 运算符行为。

### 4.2 Embedding：阿里云 DashScope（完整请求/响应链路）

**什么是 Embedding？** 把文本变成一个高维空间里的坐标点。语义相似的文本，坐标点距离近。

```
"茅台股价"  →  [0.023, -0.451, 0.789, ..., 0.112]  (1024维向量)
"贵州茅台"  →  [0.019, -0.448, 0.793, ..., 0.108]  ← 坐标很近（语义相似）
"今天天气"  →  [-0.831, 0.267, -0.143, ..., 0.521]  ← 坐标很远（语义不同）
```

**为什么要选阿里云 text-embedding-v3**：

| 模型 | 维度 | 中文效果 | 成本 |
|------|------|---------|------|
| text-embedding-v3 | 1024 | ⭐⭐⭐⭐⭐ | 低 |
| OpenAI text-embedding-3-small | 1536 | ⭐⭐⭐ | 中 |
| bge-m3（开源） | 1024 | ⭐⭐⭐⭐ | 免费但需 GPU |

1024 维意味着每个文本被表示为一个 1024 个浮点数的数组。维度越高，语义表达能力越强（但也越占存储）。

**完整的 HTTP 请求和响应**：

```python
# 请求
POST https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
Authorization: Bearer sk-xxx
Content-Type: application/json

{
    "model": "text-embedding-v3",
    "input": ["贵州茅台 最新财报", "茅台 股价 走势"]
}

# 响应
{
    "object": "list",
    "data": [
        {
            "object": "embedding",
            "index": 0,
            "embedding": [0.023415, -0.451202, 0.789341, ...]  // 1024 个浮点数
        },
        {
            "object": "embedding",
            "index": 1,
            "embedding": [0.018921, -0.448117, 0.793058, ...]
        }
    ],
    "model": "text-embedding-v3",
    "usage": {"total_tokens": 8}
}
```

**关键细节**：
- API 兼容 OpenAI 的 `/v1/embeddings` 格式（`/compatible-mode/v1/embeddings`）
- 单次最多 10 条（阿里的限制），所以代码里用 `batch_size=10` 分批
- 返回的 `data` 数组和 `input` 数组**顺序一一对应**——第 i 个 input 对应第 i 个 embedding
- 如果顺序乱了，文本和向量就会错位，检索结果完全错误

```python
def _get_embeddings(self, texts: list[str]) -> list[list[float]]:
    all_embeddings = []
    batch_size = 10  # 阿里接口限制：单次最多 10 条

    for i in range(0, len(texts), batch_size):
        batch = texts[i:i + batch_size]
        payload = {"model": self.embedding_model, "input": batch}
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        resp = requests.post(
            self.api_url,
            json=payload,
            headers=headers,
            timeout=self.http_timeout  # 30 秒超时
        )

        if resp.status_code != 200:
            raise RuntimeError(
                f"Embedding API 失败（HTTP {resp.status_code}）：{resp.text}"
            )

        data = resp.json()
        batch_vectors = data.get("data", [])

        # 防御性检查：返回向量数量必须等于输入文本数量
        if len(batch_vectors) != len(batch):
            raise RuntimeError(
                f"Embedding API 返回数量异常：输入 {len(batch)} 条，返回 {len(batch_vectors)} 条"
            )

        for item in batch_vectors:
            all_embeddings.append(item["embedding"])

    return all_embeddings
```

### 4.3 向量检索：手写 `<=>` SQL

```python
def search(self, query: str, top_k: int = 5) -> list[dict]:
    top_k = max(1, min(int(top_k), 20))  # 硬限制 1-20

    # 1. 把搜索词也变成向量
    query_vec = self._get_embeddings([query])[0]
    query_vec_literal = "[" + ",".join(str(float(x)) for x in query_vec) + "]"

    # 2. 执行相似度检索
    cur = self.conn.cursor()
    search_sql = f"""
        SELECT content, source, page, chunk_index,
               1 - (embedding <=> %s::vector) AS score
        FROM {self.collection_name}
        ORDER BY embedding <=> %s::vector
        LIMIT %s
    """
    cur.execute(search_sql, (query_vec_literal, query_vec_literal, top_k))
    rows = cur.fetchall()

    # 3. 打包结果
    return [{"text": r[0], "source": r[1], "page": r[2], "score": r[4]} for r in rows]
```

**pgvector 三种距离运算符的数学含义**：

| 运算符 | 数学含义 | 公式 | 值越小代表 | 适用场景 |
|--------|---------|------|-----------|---------|
| `<=>` | 余弦距离 | `1 - cos(θ)` | 方向越一致（语义越相似） | **文本语义检索**（推荐） |
| `<->` | 欧氏距离（L2） | `√Σ(ai-bi)²` | 坐标越接近 | 图像、数值数据 |
| `<#>` | 负内积 | `-Σ(ai×bi)` | 向量越长越"远" | 需要归一化向量的场景 |

**为什么文本检索用余弦距离**：文本向量的长度受文本长度影响（长文本通常有更大的向量模长），但语义相似度取决于方向而非长度。"茅台"和"贵州茅台"的向量方向应该接近，即使长度不同——余弦距离只关注方向，不受长度影响。

**具体例子**：
```
query_vector("茅台股价")    = [0.5, 0.8, 0.1, ...]
doc1_vector("贵州茅台股价")  = [0.48, 0.82, 0.09, ...] ← 方向很近
doc2_vector("今天天气")      = [-0.3, 0.1, 0.95, ...]  ← 方向很远

cos(query, doc1) ≈ 0.98  → 余弦距离 = 1 - 0.98 = 0.02  ← 很相似
cos(query, doc2) ≈ 0.12  → 余弦距离 = 1 - 0.12 = 0.88  ← 不相似
```

**`register_vector()` 做了什么**：
```python
from pgvector.psycopg2 import register_vector

# pgvector 定义了 VECTOR 这个新的 PostgreSQL 数据类型
# 但 psycopg2 默认不认识它——不知道怎么把 Python list 转成 VECTOR
# register_vector() 给 psycopg2 注册了一个"类型适配器"：
#   Python list[float] → PostgreSQL VECTOR 类型
#   PostgreSQL VECTOR 类型 → Python list[float]
# 不注册的话，psycopg2 看到 VECTOR 列会报类型错误
register_vector(self.conn)
```

### 4.4 为什么用 HNSW 索引

```sql
-- 生产环境建索引（部署时执行）
CREATE INDEX ON doc_chunks USING hnsw (embedding vector_cosine_ops);
```

| 索引类型 | 查询速度 | 建索引速度 | 内存 | 适用场景 |
|---------|---------|-----------|------|---------|
| IVFFlat | 中 | 快 | 低 | 数据量不大 |
| **HNSW** | **快** | 慢 | 高 | **生产环境** |

**面试话术**："HNSW 是图结构的近似最近邻索引，虽然建索引慢、内存占用高，但查询速度远快于 IVFFlat。而且它是增量构建的——新数据入库不需要重新训练聚类中心。投研场景要求低延迟响应，所以选 HNSW。"

---

## 五、检索策略：本地优先，自动降级

```python
# aipy2/app/tools/retriever_tool.py

async def run_retrieval_async(queries, mode="auto", top_k=5):
    if mode == "local":
        return await _first_non_empty_result(queries, _search_local_async, top_k)
    if mode == "web":
        return await _first_non_empty_result(queries, _search_web_async, top_k)

    # auto 模式：先本地，没命中再联网
    local_result = await _first_non_empty_result(queries, _search_local_async, top_k)
    if local_result:
        return local_result
    return await _first_non_empty_result(queries, _search_web_async, top_k)
```

**并发执行多条 query**：

```python
async def _first_non_empty_result(queries, search_fn, top_k):
    tasks = [asyncio.create_task(search_fn(q, top_k=top_k)) for q in queries]
    results = await asyncio.gather(*tasks, return_exceptions=True)
    # 按原顺序返回第一条非空结果
    for result in results:
        if isinstance(result, str) and result.strip():
            return result
    return ""
```

**关键设计**：多条搜索词并发执行，但按原始优先级返回——不是谁先回来用谁，而是优先用第一条查询词的结果。

---

## 六、文档入库流程

```python
# aipy2/app/rag/parser.py — 文档解析
# aipy2/app/rag/chunker.py — 文本分块
# aipy2/app/rag/vector_store.py — 向量化入库

# 完整流程:
# 1. 加载文档 (PDF/DOCX/MD)
# 2. 文本分块 (chunk_size=500, chunk_overlap=50)
# 3. 批量向量化 (阿里云 API, batch_size=10)
# 4. UPSERT 进 pgvector (ON CONFLICT DO NOTHING)
```

**分块参数**：`chunk_size=500` + `overlap=50`。overlap 保证跨块边界的上下文不会丢失。

---

## 七、面试速记

**Q: pgvector 的 `<=>` 运算符是什么？**
A: 余弦距离。值范围 0~2，越小越相似。`1 - <=>` 就是余弦相似度，越接近 1 越相关。

**Q: HNSW vs IVFFlat 怎么选？**
A: HNSW 查询快但建索引慢、内存高，适合生产环境。IVFFlat 建索引快但查询精度一般，适合开发或小数据量。

**Q: 为什么不用 LangChain 的 PGVector wrapper？**
A: 因为需要手写 `<=>` SQL 精确控制查询行为，以及完全控制 HNSW 索引参数。LangChain 的 PGVector 封装层会隐藏这些细节，不利于优化和面试展示。

**Q: 检索策略怎么设计？**
A: 先本地 pgvector，再联网 Tavily。多条搜索词并发执行，按优先级返回。本地覆盖已知知识，联网补充最新资讯。

**Q: 为什么选阿里云 text-embedding-v3？**
A: 1024 维高表达 + 中文效果优秀 + 兼容 OpenAI API 格式（`/compatible-mode/v1/embeddings`），切换成本低。

---

## 项目代码索引

| 文件 | 核心内容 |
|------|---------|
| `aipy2/app/rag/vector_store.py` | VectorStore 类、psycopg2、`<=>` SQL、HNSW |
| `aipy2/app/tools/retriever_tool.py` | 检索路由（auto/local/web）、并发执行 |
| `aipy2/app/rag/parser.py` | 文档加载器 |
| `aipy2/app/rag/chunker.py` | RecursiveCharacterTextSplitter |
| `aipy2/app/tools/data_fetcher.py` | 并行获取（含检索 + 行情 + 财务） |

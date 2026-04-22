import asyncio
import json
import math
from typing import Any, Literal, List
import httpx
from langchain_core.tools import tool

from app.core.config import settings
from app.rag.vector_store import VectorStore

# 初始化向量库 (RAG 核心)
my_vector = VectorStore(
    db_url=settings.DATABASE_URL,
    api_key=settings.DASH_API_KEY,
    collection_name="doc_chunks",
)

import jieba
from rank_bm25 import BM25Okapi

# -----------------------------
# 1. 核心算法：BM25 (Best Matching 25) 专业版
# -----------------------------
class BM25Engine:
    """
    【企业级检索】BM25 引擎。
    面试点：为什么在大模型时代还要 BM25？
    - 向量搜索擅长“语义”，但对“关键词/编号/专有名词”极其迟钝。
    - BM25 擅长精准匹配关键词。混合检索 (Hybrid Search) = 语义 + 词频。
    """
    def __init__(self, corpus_docs: list[dict]):
        self.raw_docs = corpus_docs
        # 1. 中文分词：使用 jieba 把文档拆成词袋
        self.tokenized_corpus = [list(jieba.cut(doc["text"])) for doc in corpus_docs]
        # 2. 初始化 BM25 算法对象
        self.bm25 = BM25Okapi(self.tokenized_corpus)

    def search(self, query: str, top_k: int = 3) -> list[dict]:
        # 对查询命令也进行分词
        tokenized_query = list(jieba.cut(query))
        # 获取得分
        scores = self.bm25.get_scores(tokenized_query)
        # 获取最相关的文档
        top_n = self.bm25.get_top_n(tokenized_query, self.raw_docs, n=top_k)
        
        results = []
        for doc in top_n:
            results.append({
                "content": f"[BM25 匹配] {doc['source']}: {doc['text']}",
                "score": 0.0 # rank_bm25 的原始分通常较大，这里可根据需要归一化
            })
        return results

# 这里模拟一个本地已经加载好的库（实际项目中通常存放在持久化索引里）
LOCAL_CORPUS = [
    {"source": "财务手册", "text": "贵州茅台2024年一季度实现净利润同比增长15.7%，超出市场预期。"},
    {"source": "行业报告", "text": "白酒板块近期表现出较强的防御属性，高端白酒批价基本稳定。"},
    {"source": "研报摘要", "text": "茅台的毛利率保持在90%以上，具有极高的护城河。"},
    {"source": "年报数据", "text": "公司拟每10股派发现金红利308.76元（含税），创历史新高。"},
]
bm25_engine = BM25Engine(LOCAL_CORPUS)

async def _search_bm25_async(query: str, top_k: int = 3) -> list[dict]:
    """【关键词路】BM25 检索。"""
    # 教学建议：在真实生产环境下，这里应该是查 Elasticsearch 或本地 Whoosh 索引
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: bm25_engine.search(query, top_k))

# -----------------------------
# 2. 向量、联网与重排序助手
# -----------------------------
async def _search_local_async(query: str, top_k: int = 3) -> str:
    loop = asyncio.get_event_loop()
    results = await loop.run_in_executor(None, lambda: my_vector.search(query, top_k=top_k))
    if not results: return ""
    formatted = [f"[本地来源 {i}] {res['source']}: {res['text']}" for i, res in enumerate(results, 1)]
    return "\n".join(formatted)

async def _search_web_async(query: str, top_k: int = 3) -> str:
    """联网搜索：使用 Tavily 引擎"""
    api_key = settings.SEARCHER_API
    if not api_key: return ""
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post("https://api.tavily.com/search", 
                                   headers={"Content-Type": "application/json"},
                                   json={
                                       "api_key": api_key,
                                       "query": query, 
                                       "max_results": top_k
                                   }, timeout=10)
            if resp.status_code == 200:
                results = resp.json().get("results", [])
                return "\n".join([f"[联网来源 {i}] {r['title']}: {r['content']}" for i, r in enumerate(results, 1)])
        except Exception: pass
    return ""

async def _rerank_results(queries: List[str], contents: List[str], top_k: int = 3) -> str:
    if not contents: return ""
    scored_items = []
    for content in contents:
        score = sum(content.count(q) for q in queries)
        scored_items.append({"content": content, "score": score})
    sorted_items = sorted(scored_items, key=lambda x: x["score"], reverse=True)[:top_k]
    return "\n\n---\n\n".join([item["content"] for item in sorted_items])

async def _rrf_fusion(vector_results: list[str], bm25_results: list[dict], top_k: int = 3) -> list[str]:
    k = 60
    scores = {}
    for rank, content in enumerate(vector_results):
        scores[content] = scores.get(content, 0) + 1.0 / (k + rank)
    for rank, doc in enumerate(bm25_results):
        content = doc["content"]
        scores[content] = scores.get(content, 0) + 1.0 / (k + rank)
    sorted_res = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [item[0] for item in sorted_res[:top_k]]

# -----------------------------
# 3. 终极异步检索入口
# -----------------------------
async def run_retrieval_async(queries: List[str], mode: Literal["local", "web", "auto"] = "auto", top_k: int = 5) -> str:
    primary_query = queries[0]
    v_task = _search_local_async(primary_query, top_k=top_k)
    b_task = _search_bm25_async(primary_query, top_k=top_k)
    w_task = _search_web_async(primary_query, top_k=top_k)
    v_res, b_res, w_res = await asyncio.gather(v_task, b_task, w_task)
    v_list = [line for line in v_res.split("\n") if line.strip()]
    fused_local = await _rrf_fusion(v_list, b_res, top_k=3)
    all_candidates = fused_local + ([w_res] if w_res else [])
    return await _rerank_results(queries, all_candidates, top_k=3)
@tool
async def search_intelligent(query: str) -> str:
    """全能智能搜索：支持本地(Vector+BM25)+联网检索，并进行 Rerank 优化。"""
    return await run_retrieval_async(queries=[query], mode="auto")

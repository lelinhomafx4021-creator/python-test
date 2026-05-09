"""
向量存储模块 - PostgreSQL + pgvector 版本

用一个 PostgreSQL 数据库同时搞定关系型数据和向量检索。
不需要额外部署 Qdrant/ChromaDB。

======== 面试核心知识点 ========

Q: pgvector 是什么？
A: PostgreSQL 的一个扩展插件，给 PG 加了一个 VECTOR 数据类型。
   装了之后可以：
   1. 用 VECTOR(512) 类型的列存储嵌入向量
   2. 用 <=> 运算符算余弦距离
   3. 用 HNSW 索引加速 ANN 搜索
   相当于在 PG 里内置了一个向量数据库。

Q: pgvector 的索引怎么选？
A: 两种索引：
   - IVFFlat: 基于聚类的倒排索引，建索引快但查询精度一般
   - HNSW: 基于图的索引，查询快且精度高，但建索引慢、内存大
   生产环境推荐 HNSW（我们用的就是这个）

Q: pgvector 的搜索运算符
A: <=> 余弦距离（值越小越相似，范围 0~2）
   <-> 欧氏距离（L2 距离）
   <#> 内积距离（负内积）
   我们用 <=> 余弦距离，和之前 Qdrant/ChromaDB 的 COSINE 是一样的

Q: 为什么用 psycopg2（同步）而不是 asyncpg（异步）？
A: 这个模块主要给入库脚本用，脚本是批处理任务，不需要异步。
   用 psycopg2 代码更简单直观，方便理解底层 SQL。
   FastAPI 的异步数据库操作用 asyncpg，各司其职。
========================
"""

import uuid
import requests
from typing import Optional

# psycopg2: PostgreSQL 的 Python 驱动（同步版）
# -binary 后缀表示预编译版本，不需要本地编译 C 代码
import psycopg2
from psycopg2.extras import execute_values

# pgvector 的 psycopg2 集成
# register_vector() 让 psycopg2 认识 VECTOR 类型
# 不注册的话，psycopg2 不知道怎么把 Python 列表转成 PG 的 vector
from pgvector.psycopg2 import register_vector

# numpy 这里不需要了，因为阿里 API 直接返回 list[float]
# 不再需要 fastembed，改用阿里云 DashScope 的 Embedding API

# 导入我们的数据结构
from app.core.logger import logger
from app.rag.parser import DocChunk


# ============================================================
# 【RAG 系统的基石：向量存储】
# ============================================================
class VectorStore:
    """
    PostgreSQL + pgvector 向量存储管理器
    
    知识点：为什么不用专门的向量数据库（如 Milvus/Chroma）？
    面试回答：因为本项目采用了“All-in-One”的设计思路。PostgreSQL 通过 pgvector 插件，
    可以在一张表里同时处理“结构化数据”（如用户 ID、文件名）和“非结构化向量”。
    这样可以极大简化系统架构，减少数据同步的开销。
    """

    def __init__(
        self,
        db_url: str,
        api_key: str,
        collection_name: str = "doc_chunks",
        embedding_model: str = "text-embedding-v3",
    ):
        """
        初始化连接
        面试点：Embedding 模型选型。
        我们这里选用阿里的 `text-embedding-v3`，它的维度是 1024，表达能力远超 512 维的小模型。
        """
        self.collection_name = collection_name
        self.api_key = api_key
        self.embedding_model = embedding_model
        self.http_timeout = 30
        
        # 阿里云官方提供的兼容 OpenAI 格式的接口地址
        self.api_url = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
        self.vector_size = 1024
        
        logger.info("[PG] 正在连接数据库...")
        self.conn = psycopg2.connect(db_url)
        self.conn.autocommit = True

        # 在注册 vector 适配器之前，先确保 pgvector 扩展已安装。
        with self.conn.cursor() as cur:
            cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        # 核心：给数据库驱动“打补丁”，让它学会处理 vector 这种新数据类型
        register_vector(self.conn)

    def create_collection(self):
        """校验 RAG 相关表是否已经由 Alembic 初始化完成。

        注意：
        - 这个方法现在不再负责“建表”
        - 它只负责“检查表是否存在”
        - 如果表不存在，会明确提示先执行 `alembic upgrade head`
        """
        cur = self.conn.cursor()
        # 保险起见，先确认 pgvector 扩展已经启用。
        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        # 检查文档切片表是否存在。
        cur.execute("SELECT to_regclass(%s)", (self.collection_name,))
        collection_exists = cur.fetchone()[0]
        # 检查文档入库登记表是否存在。
        cur.execute("SELECT to_regclass('ai_ingest_registry')")
        registry_exists = cur.fetchone()[0]
        cur.close()

        # 只要缺任意一张关键表，就直接报错，提醒先跑 Alembic。
        if not collection_exists or not registry_exists:
            raise RuntimeError(
                "Missing RAG tables. Run `alembic upgrade head` before using the vector store."
            )

    def _get_embeddings(self, texts: list[str]) -> list[list[float]]:
        """
        【中转站：把文字变成数字】
        调用阿里 API，把一句话变成一个 1024 维的坐标点。
        """
        if not texts:
            return []

        all_embeddings = []
        # 【教学修改】这里按当前阿里接口的真实限制来。
        # 我们刚才实测报错里已经明确说了：单次不能超过 10 条。
        # 所以这里改成 10，避免大批量入库时报 InvalidParameter。
        batch_size = 10

        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]
            payload = {
                "model": self.embedding_model,
                "input": batch,
            }
            headers = {
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            }
            resp = requests.post(
                self.api_url,
                json=payload,
                headers=headers,
                timeout=self.http_timeout,
            )
            if resp.status_code != 200:
                raise RuntimeError(f"Embedding API 失败（HTTP {resp.status_code}）：{resp.text}")
            
            data = resp.json()
            batch_vectors = data.get("data", [])
            if len(batch_vectors) != len(batch):
                raise RuntimeError(
                    f"Embedding API 返回数量异常：输入 {len(batch)} 条，返回 {len(batch_vectors)} 条"
                )
            # 这里按 API 返回顺序追加向量，保持与输入 texts 的顺序对齐。
            # 后面 add_documents 会用 zip(chunks, embeddings) 一一对应入库。
            for item in batch_vectors:
                all_embeddings.append(item["embedding"])

        return all_embeddings

    def add_documents(self, chunks: list[DocChunk]):
        """
        【入库：把切好的书存进书架】
        """
        if not chunks: return
        
        texts = [c.text for c in chunks]
        # 先变数字（向量）
        embeddings = self._get_embeddings(texts)
        # 关键假设：embeddings 的长度和 chunks 一致，且顺序一致。
        # 若三方接口行为变化，这里会导致文本和向量错位，检索结果会变差。
        if len(embeddings) != len(chunks):
            raise RuntimeError(
                f"向量数量与文档数量不一致：chunks={len(chunks)} embeddings={len(embeddings)}"
            )

        cur = self.conn.cursor()
        # 批量插入：使用 execute_values 在大量数据时比 executemany 更快。
        insert_sql = f"""
            INSERT INTO {self.collection_name}
                (id, content, source, page, chunk_index, file_type, embedding)
            VALUES %s
            ON CONFLICT (id) DO NOTHING
        """
        values = []
        for chunk, vec in zip(chunks, embeddings):
            values.append((
                str(uuid.uuid4()),
                chunk.text,
                chunk.metadata.get("source", ""),
                chunk.metadata.get("page"),
                chunk.metadata.get("chunk_index", 0),
                chunk.metadata.get("file_type", ""),
                vec,
            ))

        execute_values(cur, insert_sql, values)
        cur.close()

    def delete_by_sources(self, sources: list[str]):
        """
        【教学修改】按来源文件名删除旧数据。

        为什么这样做？
        1. 现在一份 PDF 重新入库时，会重新生成新的 UUID
        2. 所以同一个文件重复跑脚本，会越积越多
        3. 最简单的解决办法，就是“先删旧的，再写新的”

        这个版本先按 source 做覆盖，足够稳定，也容易理解。
        """
        cleaned_sources = [s.strip() for s in sources if s and s.strip()]
        if not cleaned_sources:
            return

        cur = self.conn.cursor()
        delete_sql = f"DELETE FROM {self.collection_name} WHERE source = ANY(%s)"
        cur.execute(delete_sql, (cleaned_sources,))
        cur.close()

    def get_source_hash(self, source: str) -> Optional[str]:
        """读取某份资料上次入库时保存的 hash。"""
        cur = self.conn.cursor()
        cur.execute(
            "SELECT file_hash FROM ai_ingest_registry WHERE source = %s",
            (source,),
        )
        row = cur.fetchone()
        cur.close()
        return row[0] if row else None

    def upsert_source_hash(self, source: str, file_hash: str):
        """更新某份资料的最新 hash。"""
        cur = self.conn.cursor()
        cur.execute(
            """
            INSERT INTO ai_ingest_registry (source, file_hash, updated_at)
            VALUES (%s, %s, NOW())
            ON CONFLICT (source)
            DO UPDATE SET
                file_hash = EXCLUDED.file_hash,
                updated_at = NOW()
            """,
            (source, file_hash),
        )
        cur.close()

    def search(self, query: str, top_k: int = 5) -> list[dict]:
        """
        【核心：语义检索】
        这是最神奇的地方：哪怕你搜“盈利”，数据库也能找到“净利润”，
        因为它算的是两个坐标点之间的【角度】（余弦距离）。
        """
        query = (query or "").strip()
        if not query:
            return []

        # 保护查询规模：最多返回 20 条，避免误传大值拖慢 SQL。
        top_k = max(1, min(int(top_k), 20))

        # 1. 先把搜索词也变成向量
        query_vec = self._get_embeddings([query])[0]
        # 格式化成 PG 认识的字符串格式 [0.1, 0.2...]
        query_vec_literal = "[" + ",".join(str(float(x)) for x in query_vec) + "]"

        cur = self.conn.cursor()
        # 2. 执行向量检索 SQL
        # <=> 是 pgvector 专用的符号，代表计算“余弦距离”
        # 1 - 距离 = 相似度。我们要找距离最近（最相似）的几条。
        search_sql = f"""
            SELECT content, source, page, chunk_index,
                   1 - (embedding <=> %s::vector) AS score
            FROM {self.collection_name}
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """
        cur.execute(search_sql, (query_vec_literal, query_vec_literal, top_k))
        rows = cur.fetchall()
        cur.close()

        # 3. 结果打包：把原始文本和得分还给 Agent
        # 注意：score 越接近 1 表示越相似。
        results = []
        for row in rows:
            results.append({
                "text": row[0],
                "source": row[1],
                "page": row[2],
                "chunk_index": row[3],
                "score": row[4],
            })
        return results

    def close(self):
        """任务完成，断开连接，释放资源"""
        if self.conn and not self.conn.closed:
            self.conn.close()

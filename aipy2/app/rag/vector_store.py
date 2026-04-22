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

# pgvector 的 psycopg2 集成
# register_vector() 让 psycopg2 认识 VECTOR 类型
# 不注册的话，psycopg2 不知道怎么把 Python 列表转成 PG 的 vector
from pgvector.psycopg2 import register_vector

# numpy 这里不需要了，因为阿里 API 直接返回 list[float]
# 不再需要 fastembed，改用阿里云 DashScope 的 Embedding API

# 导入我们的数据结构
from app.rag.parser import DocChunk


class VectorStore:
    """
    PostgreSQL + pgvector 向量存储

    和之前 Qdrant 版本的区别：
    - Qdrant: 需要单独部署一个服务，用专有 API 操作
    - pgvector: 就是 SQL，建表、插入、查询都是 SQL 语句
    - 优势：一个 PG 搞定所有，运维简单，面试能聊 SQL 优化

    集合（collection）在这里对应一张表。
    collection_name 就是表名。
    """

    def __init__(
        self,
        db_url: str,
        api_key: str,
        collection_name: str = "doc_chunks",
        embedding_model: str = "text-embedding-v3",
    ):
        """
        初始化

        参数:
        - db_url: PostgreSQL 连接地址
          格式: postgresql://用户名:密码@主机:端口/数据库名
          例: postgresql://postgres:123456@127.0.0.1:5432/ai_investor
        - api_key: 阿里云 DashScope 的 API Key
        - collection_name: 集合名（= 表名）
        - embedding_model: 嵌入模型名称，默认用阿里最新的 text-embedding-v3
        """

        self.collection_name = collection_name
        self.api_key = api_key
        self.embedding_model = embedding_model
        # ============================================================
        # 阿里云 DashScope Embedding API 的地址
        # ============================================================
        # 这是阿里官方的 OpenAI 兼容接口（和 OpenAI 的格式一模一样）
        # 面试谈资：阿里、百度、智谱等国内大厂都提供了 OpenAI 兼容的 API
        # 好处是代码几乎不用改，换个 URL 和 Key 就能切换不同厂商的模型
        self.api_url = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
        # text-embedding-v3 的向量维度是 1024
        # 比之前本地跑的 bge-small-zh (512维) 更强，语义表达能力更好
        self.vector_size = 1024
        # ============================================================
        # 连接 PostgreSQL
        # ============================================================
        # psycopg2.connect(dsn) 支持 URI 格式的连接字符串
        # 这一步会建立 TCP 连接 + 认证
        print(f"[PG] 正在连接数据库...")
        self.conn = psycopg2.connect(db_url)
        # 设置 autocommit
        # 默认 psycopg2 每条 SQL 都在事务里，需要手动 commit
        # DDL 语句（CREATE TABLE 等）建议 autocommit，省得忘 commit
        self.conn.autocommit = True

        # 注册 pgvector 类型
        # 这一步很关键！不注册的话：
        # - 写入时 Python list 不能自动转成 PG 的 vector
        # - 读取时 PG 的 vector 不能自动转成 Python numpy array
        register_vector(self.conn)

        print(f"[PG] 连接成功")
        print(f"[Embedding] 使用阿里云 DashScope 模型: {embedding_model}")
        print(f"[Embedding] 向量维度: {self.vector_size}")

    def create_collection(self):
        """
        创建向量表 + 索引
        相当于 Qdrant 的 create_collection()
        但这里是纯 SQL，面试能把建表语句说出来很加分
        """
        cur = self.conn.cursor()
        # ============================================================
        # 第 1 步：启用 pgvector 扩展
        # ============================================================
        # CREATE EXTENSION IF NOT EXISTS vector;
        # 这条 SQL 告诉 PG："请加载 vector 扩展模块"
        # 只需要在数据库里执行一次，重复执行不会报错（IF NOT EXISTS）
        # 注意：需要数据库超级用户权限（docker 默认的 postgres 用户就是）
        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        print("[PG] pgvector 扩展已启用")

        # ============================================================
        # 第 2 步：建表
        # ============================================================
        # vector({dim}) 是 pgvector 提供的数据类型
        # 相当于一个固定长度的浮点数数组
        cur.execute(f"""
            CREATE TABLE IF NOT EXISTS {self.collection_name} (
                id          TEXT PRIMARY KEY,
                content     TEXT NOT NULL,
                source      VARCHAR(255),
                page        INTEGER,
                chunk_index INTEGER DEFAULT 0,
                file_type   VARCHAR(50),
                embedding   vector({self.vector_size})
            )
        """)
        print(f"[PG] 表 '{self.collection_name}' 已创建")

        # ============================================================
        # 第 3 步：创建 HNSW 索引
        # ============================================================
        # 没有索引 → pgvector 做暴力搜索（全表扫描），数据多了巨慢
        # 有 HNSW 索引 → 近似最近邻搜索，百万级数据毫秒级返回
        #
        # 参数解释：
        # - USING hnsw: 使用 HNSW 算法建索引
        # - vector_cosine_ops: 用余弦距离运算
        #   如果用欧氏距离，换成 vector_l2_ops
        # - m = 16: 图中每个节点的最大连接数
        #   越大 → 索引越准但越占内存，一般 16-64
        # - ef_construction = 200: 建索引时的搜索宽度
        #   越大 → 索引质量越好但建得越慢，一般 100-500
        #
        # 面试热点：
        # Q: "你们向量检索的索引怎么做的？"
        # A: "用 pgvector 的 HNSW 索引，m=16, ef_construction=200，
        #     百万级数据下 top-5 检索耗时在 10ms 以内"
        cur.execute(f"""
            CREATE INDEX IF NOT EXISTS idx_{self.collection_name}_embedding
            ON {self.collection_name}
            USING hnsw (embedding vector_cosine_ops)
            WITH (m = 16, ef_construction = 200)
        """)
        print(f"[PG] HNSW 索引已创建 (m=16, ef_construction=200)")

        cur.close()

    def _get_embeddings(self, texts: list[str]) -> list[list[float]]:
        """
        调用阿里云 DashScope API 批量生成嵌入向量
        面试知识点：
        - 这里用的是阿里的 OpenAI 兼容接口
        - 请求格式和 OpenAI 的 /v1/embeddings 完全一样
        - 好处：以后想换成 OpenAI / 智谱 / 百度的模型，只需要改 URL 和 Key
        注意：DashScope 单次请求最多支持 25 条文本
        超过 25 条需要分批发送（批处理）
        """

        all_embeddings = []
        batch_size = 25  # 阿里 API 单次上限

        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]

            # 构造请求体（和 OpenAI 格式一样）
            payload = {
                "model": self.embedding_model,
                "input": batch,
            }

            # 构造请求头，Bearer Token 认证
            headers = {
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            }

            # 发送 POST 请求
            resp = requests.post(self.api_url, json=payload, headers=headers)

            # 检查返回状态码
            if resp.status_code != 200:
                raise Exception(f"DashScope API 调用失败: {resp.status_code} {resp.text}")

            data = resp.json()

            # 从返回的 JSON 中提取向量
            # 返回格式: {"data": [{"embedding": [0.1, 0.2, ...]}, ...], ...}
            for item in data["data"]:
                all_embeddings.append(item["embedding"])

        return all_embeddings

    def add_documents(self, chunks: list[DocChunk]):
        """
        批量写入文档块到 PostgreSQL

        和 Qdrant 版本的区别：
        - Qdrant: 构造 PointStruct 对象，调用 upsert API
        - pgvector: 写 INSERT SQL 语句
        """

        if not chunks:
            print("[写入] 没有数据需要写入")
            return

        texts = [c.text for c in chunks]

        # ============================================================
        # 调用阿里云 API 批量生成嵌入向量
        # ============================================================
        print(f"[写入] 正在为 {len(texts)} 个文档块生成嵌入向量（阿里云 API）...")
        embeddings = self._get_embeddings(texts)

        # ============================================================
        # 批量 INSERT
        # ============================================================
        cur = self.conn.cursor()

        # executemany: 批量执行同一条 SQL，参数不同
        # 比循环里一条条 execute 快很多（减少网络往返）
        #
        # %s 是 psycopg2 的参数占位符（不是 f-string！）
        # psycopg2 会自动处理 SQL 注入防护
        # 千万不要用 f-string 拼 SQL！！！面试必问的安全问题
        insert_sql = f"""
            INSERT INTO {self.collection_name}
                (id, content, source, page, chunk_index, file_type, embedding)
            VALUES
                (%s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO NOTHING
        """
        # ON CONFLICT DO NOTHING: 如果 id 重复就跳过（幂等操作）

        # 准备参数列表
        params = []
        for chunk, vec in zip(chunks, embeddings):
            params.append((
                str(uuid.uuid4()),                       # id: UUID
                chunk.text,                               # content: 原始文本
                chunk.metadata.get("source", ""),         # source: 来源文件
                chunk.metadata.get("page"),               # page: 页码（可能为 None）
                chunk.metadata.get("chunk_index", 0),     # chunk_index: 切片序号
                chunk.metadata.get("file_type", ""),      # file_type: 文件类型
                vec,                                      # embedding: 向量（已经是 list[float]）
            ))

        # executemany 一次性写入所有数据
        cur.executemany(insert_sql, params)

        # 因为开了 autocommit，所以不需要手动 conn.commit()
        cur.close()
        print(f"[写入] 成功写入 {len(params)} 条记录到 '{self.collection_name}'")

    def search(self, query: str, top_k: int = 5) -> list[dict]:
        """
        相似度检索 - 用 pgvector 的 <=> 运算符

        SQL 解释：
        SELECT content, source, page, chunk_index,
               1 - (embedding <=> %s) AS score    -- 余弦相似度 = 1 - 余弦距离
        FROM doc_chunks
        ORDER BY embedding <=> %s                  -- 按距离排序（近→远）
        LIMIT 5

        注意：
        - <=> 返回的是"距离"（越小越相似），范围 0~2
        - 我们转换成"相似度"（越大越相似）：similarity = 1 - distance
        - 这样和 Qdrant 的 score 含义一致，方便对比
        """

        # 把 query 变成向量（调用阿里云 API）
        query_vec = self._get_embeddings([query])[0]

        # 关键修复：显式按 pgvector 文本格式传参，避免被 psycopg2 识别成 numeric[]
        # pgvector 字面量格式示例: [0.1,0.2,0.3]
        query_vec_literal = "[" + ",".join(str(float(x)) for x in query_vec) + "]"

        cur = self.conn.cursor()

        # 搜索 SQL
        # 注意这里显式做 ::vector 强制类型转换，确保 <=> 运算符可用
        search_sql = f"""
            SELECT content, source, page, chunk_index,
                   1 - (embedding <=> %s::vector) AS score
            FROM {self.collection_name}
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """

        cur.execute(search_sql, (query_vec_literal, query_vec_literal, top_k))

        # fetchall() 返回所有结果行
        rows = cur.fetchall()
        cur.close()

        # 整理成字典列表
        results = []
        for row in rows:
            results.append({
                "text": row[0],         # content
                "source": row[1],       # source
                "page": row[2],         # page
                "chunk_index": row[3],  # chunk_index
                "score": row[4],        # similarity score (0~1)
            })

        return results

    def count(self) -> int:
        """查看表里有多少条记录"""
        cur = self.conn.cursor()
        cur.execute(f"SELECT COUNT(*) FROM {self.collection_name}")
        result = cur.fetchone()[0]
        cur.close()
        return result

    def delete_collection(self):
        """删除表（慎用！数据不可恢复）"""
        cur = self.conn.cursor()
        cur.execute(f"DROP TABLE IF EXISTS {self.collection_name}")
        cur.close()
        print(f"[PG] 已删除表 '{self.collection_name}'")

    def close(self):
        """关闭数据库连接"""
        if self.conn and not self.conn.closed:
            self.conn.close()
            print("[PG] 数据库连接已关闭")

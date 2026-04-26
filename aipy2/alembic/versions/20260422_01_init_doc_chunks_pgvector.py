"""初始化 doc_chunks 表并启用 pgvector。

修订 ID: 20260422_01
依赖修订: 无
创建时间: 2026-04-22 16:00:00
"""

from alembic import op

# Alembic 修订元信息
revision = "20260422_01"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    """执行升级：创建扩展、数据表与索引。"""
    # 1) 启用 pgvector 扩展
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    # 2) 创建 doc_chunks 数据表
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS doc_chunks (
            id          TEXT PRIMARY KEY,
            content     TEXT NOT NULL,
            source      VARCHAR(255),
            page        INTEGER,
            chunk_index INTEGER DEFAULT 0,
            file_type   VARCHAR(50),
            embedding   vector(1024)
        )
        """
    )

    # 3) 创建向量索引（HNSW）
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_doc_chunks_embedding
        ON doc_chunks
        USING hnsw (embedding vector_cosine_ops)
        WITH (m = 16, ef_construction = 200)
        """
    )

    # 4) 创建常用筛选索引
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_doc_chunks_source
        ON doc_chunks (source)
        """
    )


def downgrade() -> None:
    """执行回滚：删除索引与数据表。"""
    op.execute("DROP INDEX IF EXISTS idx_doc_chunks_source")
    op.execute("DROP INDEX IF EXISTS idx_doc_chunks_embedding")
    op.execute("DROP TABLE IF EXISTS doc_chunks")

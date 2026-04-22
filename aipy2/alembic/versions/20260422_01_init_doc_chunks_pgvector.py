"""init doc_chunks with pgvector

Revision ID: 20260422_01
Revises: 
Create Date: 2026-04-22 16:00:00
"""

from alembic import op

# revision identifiers, used by Alembic.
revision = "20260422_01"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 1) pgvector extension
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    # 2) doc_chunks table
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

    # 3) vector index (HNSW)
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_doc_chunks_embedding
        ON doc_chunks
        USING hnsw (embedding vector_cosine_ops)
        WITH (m = 16, ef_construction = 200)
        """
    )

    # 4) common filter index
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_doc_chunks_source
        ON doc_chunks (source)
        """
    )


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS idx_doc_chunks_source")
    op.execute("DROP INDEX IF EXISTS idx_doc_chunks_embedding")
    op.execute("DROP TABLE IF EXISTS doc_chunks")

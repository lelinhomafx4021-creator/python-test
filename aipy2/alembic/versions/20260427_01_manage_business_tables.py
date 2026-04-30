"""初始化项目业务表结构。

当前项目约定：
1. 这份迁移文件统一管理“项目自己负责的业务表”
2. LangGraph 自己的 checkpoint 表不放这里，由框架的 setup() 自管
3. 正式初始化数据库结构时，执行 `uv run alembic upgrade head`
"""

# `op` 是 Alembic 提供的迁移操作入口。
from alembic import op

# 当前迁移文件自己的版本号。
revision = "20260427_01"
# 没有前置版本，表示这是当前整理后的“初始迁移”。
down_revision = None
# 下面两个一般保持默认即可。
branch_labels = None
depends_on = None


def upgrade() -> None:
    """升级迁移：把数据库结构升到当前版本。"""
    # 先确保 PostgreSQL 已启用 pgvector 扩展。
    # `doc_chunks.embedding` 会依赖这个扩展里的 `vector` 类型。
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    # 创建 RAG 文档切片表。
    # 这张表用来存储被切片后的文档正文、来源信息和向量。
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS doc_chunks (
            id TEXT PRIMARY KEY,
            content TEXT NOT NULL,
            source VARCHAR(255),
            page INTEGER,
            chunk_index INTEGER DEFAULT 0,
            file_type VARCHAR(50),
            embedding vector(1024)
        )
        """
    )
    # 为向量检索建立 HNSW 索引。
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_doc_chunks_embedding
        ON doc_chunks
        USING hnsw (embedding vector_cosine_ops)
        WITH (m = 16, ef_construction = 200)
        """
    )
    # 为常见的来源过滤建立普通索引。
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_doc_chunks_source
        ON doc_chunks (source)
        """
    )

    # 创建 Agent 运行审计表。
    # 这张表记录一次 AI 工作流运行的输入、输出、状态和性能指标。
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS ai_agent_runs (
            id BIGSERIAL PRIMARY KEY,
            trace_id VARCHAR(64) NOT NULL,
            thread_id VARCHAR(256) NOT NULL,
            user_id VARCHAR(64) NOT NULL DEFAULT 'customer_pro',
            query TEXT NOT NULL,
            final_answer TEXT NOT NULL DEFAULT '',
            status VARCHAR(32) NOT NULL DEFAULT 'success',
            error_message TEXT NOT NULL DEFAULT '',
            response_mode VARCHAR(16) NOT NULL DEFAULT 'stream',
            source VARCHAR(64) NOT NULL DEFAULT 'Self-RAG-v2',
            use_kb BOOLEAN NOT NULL DEFAULT FALSE,
            retry_count INTEGER NOT NULL DEFAULT 0,
            total_tokens INTEGER NOT NULL DEFAULT 0,
            first_step_ms INTEGER NOT NULL DEFAULT 0,
            first_content_ms INTEGER NOT NULL DEFAULT 0,
            duration_ms INTEGER NOT NULL DEFAULT 0,
            stage_path TEXT NOT NULL DEFAULT '[]',
            critic_feedback TEXT NOT NULL DEFAULT '',
            review_status VARCHAR(32) NOT NULL DEFAULT '',
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            finished_at TIMESTAMP NULL
        )
        """
    )
    # 审计表常用查询维度：trace_id、thread_id、created_at。
    op.execute(
        "CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_trace_id ON ai_agent_runs (trace_id)"
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_thread_id ON ai_agent_runs (thread_id)"
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_created_at ON ai_agent_runs (created_at)"
    )

    # 创建聊天历史表。
    # 这张表存一轮轮对话记录，用于会话恢复和历史查看。
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS ai_chat_turns (
            id BIGSERIAL PRIMARY KEY,
            user_id VARCHAR(64) NOT NULL,
            session_id VARCHAR(128) NOT NULL,
            thread_id VARCHAR(256) NOT NULL,
            trace_id VARCHAR(64) NOT NULL,
            query TEXT NOT NULL,
            answer TEXT NOT NULL,
            intent VARCHAR(32) NOT NULL,
            source VARCHAR(64) NOT NULL,
            review_passed BOOLEAN NOT NULL DEFAULT FALSE,
            response_mode VARCHAR(16) NOT NULL DEFAULT 'sync',
            a2a_count INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """
    )
    # 为“按用户倒序查聊天历史”建立索引。
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_created
        ON ai_chat_turns (user_id, created_at DESC)
        """
    )
    # 为“按用户 + 会话查对话记录”建立组合索引。
    op.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_session_created
        ON ai_chat_turns (user_id, session_id, created_at DESC)
        """
    )
    # 下面两个 ALTER 是兼容历史场景：
    # 如果某些旧环境已经提前有表，但缺字段，就在这里补齐。
    op.execute(
        """
        ALTER TABLE ai_chat_turns
        ADD COLUMN IF NOT EXISTS response_mode VARCHAR(16) NOT NULL DEFAULT 'sync'
        """
    )
    op.execute(
        """
        ALTER TABLE ai_chat_turns
        ADD COLUMN IF NOT EXISTS a2a_count INTEGER NOT NULL DEFAULT 0
        """
    )

    # 创建用户画像表。
    # 这张表存用户偏好信息，例如风险等级和关注板块。
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS user_profiles (
            user_id VARCHAR(255) PRIMARY KEY,
            risk_level VARCHAR(32) NOT NULL DEFAULT 'mid',
            interested_sectors TEXT NULL,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """
    )

    # 创建文档入库登记表。
    # 这张表不存正文，只存文件 hash，用来判断文档是否重复入库。
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS ai_ingest_registry (
            source VARCHAR(255) PRIMARY KEY,
            file_hash TEXT NOT NULL,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """
    )


def downgrade() -> None:
    """回滚迁移：把当前版本创建的表和索引删掉。"""
    # 先删依赖较少的表。
    op.execute("DROP TABLE IF EXISTS ai_ingest_registry")
    op.execute("DROP TABLE IF EXISTS user_profiles")

    # 删除聊天历史相关索引和表。
    op.execute("DROP INDEX IF EXISTS idx_ai_chat_turns_user_session_created")
    op.execute("DROP INDEX IF EXISTS idx_ai_chat_turns_user_created")
    op.execute("DROP TABLE IF EXISTS ai_chat_turns")

    # 删除审计表相关索引和表。
    op.execute("DROP INDEX IF EXISTS idx_ai_agent_runs_created_at")
    op.execute("DROP INDEX IF EXISTS idx_ai_agent_runs_thread_id")
    op.execute("DROP INDEX IF EXISTS idx_ai_agent_runs_trace_id")
    op.execute("DROP TABLE IF EXISTS ai_agent_runs")

    # 最后删除 RAG 表和索引。
    op.execute("DROP INDEX IF EXISTS idx_doc_chunks_source")
    op.execute("DROP INDEX IF EXISTS idx_doc_chunks_embedding")
    op.execute("DROP TABLE IF EXISTS doc_chunks")

"""初始化 ai_agent_runs 技术审计表。

修订 ID: 20260426_01
依赖修订: 20260422_01
创建时间: 2026-04-26 13:40:00
"""

from alembic import op


revision = "20260426_01"
down_revision = "20260422_01"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """升级迁移：创建 ai_agent_runs 表及核心索引。"""
    # 创建审计表（若不存在则创建，避免重复执行报错）
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS ai_agent_runs (
            id               BIGSERIAL PRIMARY KEY,
            trace_id         VARCHAR(64) NOT NULL,
            thread_id        VARCHAR(256) NOT NULL,
            user_id          VARCHAR(64) NOT NULL DEFAULT 'customer_pro',
            query            TEXT NOT NULL,
            final_answer     TEXT NOT NULL DEFAULT '',
            status           VARCHAR(32) NOT NULL DEFAULT 'success',
            error_message    TEXT NOT NULL DEFAULT '',
            response_mode    VARCHAR(16) NOT NULL DEFAULT 'stream',
            source           VARCHAR(64) NOT NULL DEFAULT 'Self-RAG-v2',
            use_kb           BOOLEAN NOT NULL DEFAULT FALSE,
            retry_count      INTEGER NOT NULL DEFAULT 0,
            total_tokens     INTEGER NOT NULL DEFAULT 0,
            first_step_ms    INTEGER NOT NULL DEFAULT 0,
            first_content_ms INTEGER NOT NULL DEFAULT 0,
            duration_ms      INTEGER NOT NULL DEFAULT 0,
            stage_path       TEXT NOT NULL DEFAULT '[]',
            critic_feedback  TEXT NOT NULL DEFAULT '',
            review_status    VARCHAR(32) NOT NULL DEFAULT '',
            created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            finished_at      TIMESTAMP NULL
        )
        """
    )
    # 按常见查询维度建立索引：trace_id / thread_id / created_at
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_trace_id ON ai_agent_runs (trace_id)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_thread_id ON ai_agent_runs (thread_id)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_created_at ON ai_agent_runs (created_at)")


def downgrade() -> None:
    """回滚迁移：删除索引并移除 ai_agent_runs 表。"""
    op.execute("DROP INDEX IF EXISTS idx_ai_agent_runs_created_at")
    op.execute("DROP INDEX IF EXISTS idx_ai_agent_runs_thread_id")
    op.execute("DROP INDEX IF EXISTS idx_ai_agent_runs_trace_id")
    op.execute("DROP TABLE IF EXISTS ai_agent_runs")

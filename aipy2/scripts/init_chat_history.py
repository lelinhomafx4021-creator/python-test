"""初始化聊天历史表（标准化版本）。

运行：
    uv run python -m scripts.init_chat_history
"""

from pathlib import Path
import sys

import psycopg2

ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from app.core.config import settings


DDL = """
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
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_created
ON ai_chat_turns (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_session_created
ON ai_chat_turns (user_id, session_id, created_at DESC);

ALTER TABLE ai_chat_turns
    ADD COLUMN IF NOT EXISTS response_mode VARCHAR(16) NOT NULL DEFAULT 'sync';

ALTER TABLE ai_chat_turns
    ADD COLUMN IF NOT EXISTS a2a_count INTEGER NOT NULL DEFAULT 0;
"""


if __name__ == "__main__":
    # 连接数据库执行 DDL，确保聊天历史表和索引存在
    with psycopg2.connect(settings.database_url) as conn:
        conn.autocommit = True
        with conn.cursor() as cur:
            # 一次性执行建表 + 索引 + 兼容性 ALTER
            cur.execute(DDL)
    print("ai_chat_turns 初始化/升级完成。")

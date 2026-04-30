"""
███ 聊天历史表初始化脚本 ███

============================================================
【功能】一键创建 AI 聊天历史相关的数据库表和索引。

使用方法：
    uv run python -m scripts.init_chat_history

这个脚本用"原生 SQL（DDL）"直接建表，
而不是通过 ORM 的 create_all()。
为什么？

优点：
1. 精确控制：可以写 CREATE INDEX IF NOT EXISTS、ADD COLUMN IF NOT EXISTS
2. 幂等性：多次执行不会报错（IF NOT EXISTS 保护）
3. 可审查：每行 SQL 都是标准语句，DBA 能直接审

缺点：
1. 和 ORM 模型定义可能不同步（但这个项目目前模型很简单）
2. 不跨数据库（PostgreSQL 特有语法）

注意：
- 正式环境的表管理已统一交给 Alembic（alembic/versions/...）
- 这个脚本只是本地快速初始化/升级的"方便工具"
- 如果 Alembic 已跑过，这个脚本再执行不会冲突（IF NOT EXISTS）
"""

from pathlib import Path
import sys

import psycopg2  # PostgreSQL 的 Python 驱动（同步版）

# 让脚本能直接 `python -m scripts.init_chat_history` 运行
ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from app.core.config import settings


# ============================================================
# DDL（数据定义语言）—— 建表 + 建索引 + 补字段
# ============================================================
DDL = """
CREATE TABLE IF NOT EXISTS ai_chat_turns (
    id BIGSERIAL PRIMARY KEY,                    -- 自增主键（BIGSERIAL=自动递增的64位整数）
    user_id VARCHAR(64) NOT NULL,                -- 用户 ID
    session_id VARCHAR(128) NOT NULL,            -- 会话 ID（一次连续对话）
    thread_id VARCHAR(256) NOT NULL,             -- 线程 ID（LangGraph 的记忆隔离键）
    trace_id VARCHAR(64) NOT NULL,               -- 链路追踪 ID
    query TEXT NOT NULL,                         -- 用户问题
    answer TEXT NOT NULL,                        -- AI 回答
    intent VARCHAR(32) NOT NULL,                 -- 意图识别结果
    source VARCHAR(64) NOT NULL,                 -- 答案来源
    review_passed BOOLEAN NOT NULL DEFAULT FALSE, -- 评审是否通过
    response_mode VARCHAR(16) NOT NULL DEFAULT 'sync', -- 响应模式（sync/stream）
    a2a_count INTEGER NOT NULL DEFAULT 0,        -- Agent-to-Agent 调用次数
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW() -- 创建时间（带时区）
);

-- 索引：按用户 + 时间倒序查聊天记录
-- 这是最常用的查询模式："用户最近的对话"
CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_created
ON ai_chat_turns (user_id, created_at DESC);

-- 索引：按用户 + 会话 + 时间倒序
-- 用于"查看某个会话的完整对话历史"
CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_session_created
ON ai_chat_turns (user_id, session_id, created_at DESC);

-- 兼容旧环境：如果表已存在但缺字段，用 ALTER 补齐
-- ADD COLUMN IF NOT EXISTS 是 pg 9.6+ 的特性
ALTER TABLE ai_chat_turns
    ADD COLUMN IF NOT EXISTS response_mode VARCHAR(16) NOT NULL DEFAULT 'sync';

ALTER TABLE ai_chat_turns
    ADD COLUMN IF NOT EXISTS a2a_count INTEGER NOT NULL DEFAULT 0;
"""


if __name__ == "__main__":
    # 连接数据库并执行 DDL
    # 使用 with 管理连接：脚本结束（或中途报错）自动关闭
    with psycopg2.connect(settings.DATABASE_URL) as conn:
        # autocommit=True: 每条语句自动提交，不需要手动 conn.commit()
        # DDL（建表语句）需要这个，否则 PostgreSQL 会在事务里执行 DDL
        conn.autocommit = True
        with conn.cursor() as cur:
            # 一次性把建表、建索引、补字段的语句全部执行
            cur.execute(DDL)
    print("ai_chat_turns 初始化/升级完成。")

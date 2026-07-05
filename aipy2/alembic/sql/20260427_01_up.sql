CREATE EXTENSION IF NOT EXISTS vector;
--;;
CREATE TABLE IF NOT EXISTS doc_chunks (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    source VARCHAR(255),
    page INTEGER,
    chunk_index INTEGER DEFAULT 0,
    file_type VARCHAR(50),
    embedding vector(1024)
);
--;;
CREATE INDEX IF NOT EXISTS idx_doc_chunks_embedding
ON doc_chunks
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);
--;;
CREATE INDEX IF NOT EXISTS idx_doc_chunks_source
ON doc_chunks (source);
--;;
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
);
--;;
CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_trace_id
ON ai_agent_runs (trace_id);
--;;
CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_thread_id
ON ai_agent_runs (thread_id);
--;;
CREATE INDEX IF NOT EXISTS idx_ai_agent_runs_created_at
ON ai_agent_runs (created_at);
--;;
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
--;;
CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_created
ON ai_chat_turns (user_id, created_at DESC);
--;;
CREATE INDEX IF NOT EXISTS idx_ai_chat_turns_user_session_created
ON ai_chat_turns (user_id, session_id, created_at DESC);
--;;
ALTER TABLE ai_chat_turns
ADD COLUMN IF NOT EXISTS response_mode VARCHAR(16) NOT NULL DEFAULT 'sync';
--;;
ALTER TABLE ai_chat_turns
ADD COLUMN IF NOT EXISTS a2a_count INTEGER NOT NULL DEFAULT 0;
--;;
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'mid',
    interested_sectors TEXT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
--;;
CREATE TABLE IF NOT EXISTS ai_ingest_registry (
    source VARCHAR(255) PRIMARY KEY,
    file_hash TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

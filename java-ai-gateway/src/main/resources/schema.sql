-- Java 业务中台主存储：聊天历史表

CREATE TABLE IF NOT EXISTS ai_chat_turns (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    thread_id VARCHAR(256) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    `query` TEXT NOT NULL,
    answer TEXT NOT NULL,
    intent VARCHAR(32) NOT NULL,
    source VARCHAR(64) NOT NULL,
    review_passed TINYINT(1) NOT NULL DEFAULT 0,
    response_mode VARCHAR(16) NOT NULL DEFAULT 'sync',
    a2a_count INT NOT NULL DEFAULT 0,
    title VARCHAR(255) DEFAULT NULL COMMENT '会话标题，由 AI 异步生成',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_chat_turns_user_created (user_id, created_at),
    INDEX idx_ai_chat_turns_user_session_created (user_id, session_id, created_at)
);

-- AI 聊天审计流水表 (用于统计和合规)
CREATE TABLE IF NOT EXISTS ai_chat_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    endpoint VARCHAR(128),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户基本信息表 (简历项必备：RBAC 基础)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '加密后的密码',
    phone VARCHAR(20) COMMENT '手机号',
    status TINYINT(1) DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 插入一条测试数据 (默认密码: 123456)
INSERT IGNORE INTO users (id, username, password, phone, status) 
VALUES (1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '13800138000', 1);

-- V1__Initial_Schema.sql
-- Flyway 自动化建表脚本

-- 1. 聊天历史表
CREATE TABLE IF NOT EXISTS `ai_chat_turns` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户唯一标识',
  `session_id` varchar(64) NOT NULL COMMENT '业务会话ID',
  `thread_id` varchar(128) DEFAULT NULL COMMENT 'AI 状态对应 Thread ID',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '全链路追踪ID',
  `title` varchar(200) DEFAULT NULL COMMENT 'AI 拟定的简述标题',
  `query` text NOT NULL COMMENT '用户提问内容',
  `answer` longtext COMMENT 'AI 回答内容',
  `intent` varchar(100) DEFAULT NULL COMMENT '语义意图分类',
  `source` varchar(255) DEFAULT NULL COMMENT '数据来源渠道',
  `review_passed` tinyint(1) DEFAULT '1' COMMENT '是否合规通过',
  `response_mode` varchar(32) DEFAULT 'sync' COMMENT '响应模式',
  `a2a_count` int(11) DEFAULT '0' COMMENT '流转次数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天历史表';

-- 2. 异步审计日志表
CREATE TABLE IF NOT EXISTS `ai_chat_audit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(128) NOT NULL COMMENT '追踪ID',
  `user_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `endpoint` varchar(255) DEFAULT NULL COMMENT 'API路径',
  `message` text COMMENT '上下文',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 审计日志表';

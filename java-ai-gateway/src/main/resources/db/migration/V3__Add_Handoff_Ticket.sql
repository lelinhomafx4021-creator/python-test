CREATE TABLE IF NOT EXISTS `ai_handoff_tickets` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(128) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `thread_id` varchar(128) DEFAULT NULL,
  `query` text NOT NULL,
  `handoff_reason` varchar(128) DEFAULT NULL,
  `handoff_summary` text,
  `status` varchar(32) NOT NULL DEFAULT 'open',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_id` (`trace_id`),
  KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 人工兜底工单表';

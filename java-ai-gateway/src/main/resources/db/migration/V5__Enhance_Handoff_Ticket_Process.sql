ALTER TABLE `ai_handoff_tickets`
  ADD COLUMN `process_note` text NULL COMMENT '管理员处理备注' AFTER `status`,
  ADD COLUMN `response_message` text NULL COMMENT '回复给用户的处理结果' AFTER `process_note`,
  ADD COLUMN `handled_by` varchar(64) NULL COMMENT '处理人用户名' AFTER `response_message`,
  ADD COLUMN `handled_at` datetime NULL COMMENT '处理时间' AFTER `handled_by`,
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `handled_at`;

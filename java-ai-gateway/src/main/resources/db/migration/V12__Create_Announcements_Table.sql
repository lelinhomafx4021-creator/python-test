-- V12__Create_Announcements_Table.sql
-- 系统公告表：管理员发布系统维护、功能更新等公告，所有用户可见

CREATE TABLE IF NOT EXISTS `announcements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(128) NOT NULL COMMENT '公告标题',
  `content` text NOT NULL COMMENT '公告内容',
  `type` varchar(32) NOT NULL DEFAULT 'notice' COMMENT '类型：notice/maintenance/urgent',
  `status` varchar(16) NOT NULL DEFAULT 'draft' COMMENT '状态：draft/published/archived',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX idx_announcements_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

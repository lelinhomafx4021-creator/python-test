CREATE TABLE IF NOT EXISTS `user_notifications` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `category` varchar(32) NOT NULL DEFAULT 'system' COMMENT '通知分类',
    `title` varchar(128) NOT NULL COMMENT '通知标题',
    `content` varchar(255) NOT NULL COMMENT '通知内容',
    `status` varchar(16) NOT NULL DEFAULT 'unread' COMMENT '读取状态',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `read_at` datetime DEFAULT NULL COMMENT '读取时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_notifications_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知记录表';

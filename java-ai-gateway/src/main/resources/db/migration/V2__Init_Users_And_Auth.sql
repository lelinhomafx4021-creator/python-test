CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt 密码摘要',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=启用 0=禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

INSERT INTO `users` (`username`, `password`, `phone`, `status`)
SELECT 'admin', '$2a$10$oZpluI/1y4wtJbwkKTblMe4kQ6AVy8rUlc44Q8o76rmVb9t/B.Pb6', '13800000000', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `users` WHERE `username` = 'admin'
);

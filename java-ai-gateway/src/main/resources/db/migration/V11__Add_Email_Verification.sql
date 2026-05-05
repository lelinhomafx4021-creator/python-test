ALTER TABLE `users`
  ADD COLUMN `email` varchar(128) DEFAULT NULL COMMENT '邮箱' AFTER `phone`;

CREATE UNIQUE INDEX `uk_users_email` ON `users` (`email`);

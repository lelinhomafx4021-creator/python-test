CREATE TABLE IF NOT EXISTS `transaction_logs` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `event_type` varchar(32) NOT NULL COMMENT '事件类型：ORDER_PLACED/ORDER_FILLED/ORDER_CANCELLED/DEPOSIT/WITHDRAW',
    `symbol` varchar(16) DEFAULT NULL COMMENT '股票代码（交易类事件才有）',
    `side` varchar(8) DEFAULT NULL COMMENT '买卖方向：BUY/SELL',
    `quantity` int DEFAULT NULL COMMENT '数量',
    `price` decimal(18,4) DEFAULT NULL COMMENT '价格',
    `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
    `balance_after` decimal(18,2) DEFAULT NULL COMMENT '操作后余额',
    `description` varchar(255) DEFAULT NULL COMMENT '描述',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水日志表';

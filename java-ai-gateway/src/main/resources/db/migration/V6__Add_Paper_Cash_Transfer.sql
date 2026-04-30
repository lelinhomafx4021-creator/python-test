CREATE TABLE IF NOT EXISTS `paper_cash_transfers` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `account_id` bigint NOT NULL COMMENT '模拟账户 ID',
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `direction` varchar(16) NOT NULL DEFAULT 'deposit' COMMENT '资金方向',
    `channel_code` varchar(32) NOT NULL DEFAULT 'mock_gateway' COMMENT '渠道编码',
    `channel_name` varchar(64) NOT NULL DEFAULT '演示支付通道' COMMENT '渠道名称',
    `out_trade_no` varchar(64) NOT NULL COMMENT '商户订单号',
    `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '渠道流水号',
    `amount` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额',
    `status` varchar(32) NOT NULL DEFAULT 'success' COMMENT '状态',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `paid_at` datetime DEFAULT NULL COMMENT '到账时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_cash_transfers_out_trade` (`out_trade_no`),
    KEY `idx_paper_cash_transfers_account` (`account_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟资金充值转账记录表';

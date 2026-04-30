-- V4__Phase1_Investor_Product.sql
-- 投顾会员终端一期：会员、自选、行情、模拟交易、AI 扩展表

ALTER TABLE `users`
    ADD COLUMN `password_hash` varchar(255) DEFAULT NULL COMMENT 'BCrypt 密码摘要（新字段）' AFTER `username`,
    ADD COLUMN `nickname` varchar(64) DEFAULT NULL COMMENT '用户昵称' AFTER `phone`,
    ADD COLUMN `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像地址' AFTER `nickname`,
    ADD COLUMN `role` varchar(32) NOT NULL DEFAULT 'normal' COMMENT '用户角色：guest/normal/vip/admin' AFTER `avatar_url`,
    ADD COLUMN `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间' AFTER `status`;

UPDATE `users`
SET `password_hash` = `password`
WHERE `password_hash` IS NULL;

UPDATE `users`
SET `nickname` = `username`
WHERE `nickname` IS NULL OR `nickname` = '';

UPDATE `users`
SET `role` = 'admin'
WHERE `username` = 'admin';

CREATE TABLE IF NOT EXISTS `user_profiles` (
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `risk_level` varchar(32) DEFAULT 'balanced' COMMENT '风险等级',
    `investment_years` int DEFAULT 0 COMMENT '投资年限',
    `interested_sectors` varchar(255) DEFAULT NULL COMMENT '感兴趣板块，逗号分隔',
    `bio` varchar(255) DEFAULT NULL COMMENT '个性化简介',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

CREATE TABLE IF NOT EXISTS `membership_plans` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `plan_code` varchar(32) NOT NULL COMMENT '方案编码',
    `plan_name` varchar(64) NOT NULL COMMENT '方案名称',
    `price` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '价格',
    `billing_cycle` varchar(32) NOT NULL DEFAULT 'monthly' COMMENT '计费周期',
    `quota_json` text COMMENT '默认配额 JSON',
    `status` varchar(32) NOT NULL DEFAULT 'enabled' COMMENT '状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_membership_plan_code` (`plan_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员方案表';

CREATE TABLE IF NOT EXISTS `user_memberships` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `plan_code` varchar(32) NOT NULL COMMENT '会员方案编码',
    `start_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_at` datetime DEFAULT NULL COMMENT '结束时间',
    `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    `auto_renew` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自动续费',
    `source` varchar(64) DEFAULT 'system_default' COMMENT '开通来源',
    PRIMARY KEY (`id`),
    KEY `idx_user_memberships_user` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会员关系表';

CREATE TABLE IF NOT EXISTS `user_feature_quotas` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `feature_code` varchar(64) NOT NULL COMMENT '功能编码',
    `period_type` varchar(32) NOT NULL DEFAULT 'permanent' COMMENT '周期类型',
    `limit_count` int NOT NULL DEFAULT 0 COMMENT '配额上限',
    `used_count` int NOT NULL DEFAULT 0 COMMENT '已使用数量',
    `reset_at` datetime DEFAULT NULL COMMENT '下次重置时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_feature_quota` (`user_id`, `feature_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户功能配额表';

CREATE TABLE IF NOT EXISTS `stocks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `symbol` varchar(16) NOT NULL COMMENT '股票代码',
    `name` varchar(64) NOT NULL COMMENT '股票名称',
    `exchange` varchar(16) DEFAULT NULL COMMENT '交易所',
    `market` varchar(32) DEFAULT NULL COMMENT '市场',
    `sector_code` varchar(64) DEFAULT NULL COMMENT '板块编码',
    `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stocks_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票主数据表';

CREATE TABLE IF NOT EXISTS `sectors` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `sector_code` varchar(64) NOT NULL COMMENT '板块编码',
    `sector_name` varchar(64) NOT NULL COMMENT '板块名称',
    `parent_code` varchar(64) DEFAULT NULL COMMENT '父级板块编码',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sectors_code` (`sector_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块定义表';

CREATE TABLE IF NOT EXISTS `market_quotes` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `symbol` varchar(16) NOT NULL COMMENT '股票代码',
    `last_price` decimal(18,4) DEFAULT NULL COMMENT '最新价',
    `change_pct` decimal(10,4) DEFAULT NULL COMMENT '涨跌幅',
    `change_amount` decimal(18,4) DEFAULT NULL COMMENT '涨跌额',
    `high_price` decimal(18,4) DEFAULT NULL COMMENT '最高价',
    `low_price` decimal(18,4) DEFAULT NULL COMMENT '最低价',
    `open_price` decimal(18,4) DEFAULT NULL COMMENT '开盘价',
    `volume` decimal(20,2) DEFAULT NULL COMMENT '成交量',
    `turnover` decimal(20,2) DEFAULT NULL COMMENT '成交额',
    `turnover_rate` decimal(10,4) DEFAULT NULL COMMENT '换手率',
    `amplitude` decimal(10,4) DEFAULT NULL COMMENT '振幅',
    `quote_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '行情时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_market_quotes_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行情快照表';

CREATE TABLE IF NOT EXISTS `watchlists` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `name` varchar(64) NOT NULL COMMENT '分组名称',
    `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认分组',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_watchlists_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自选分组表';

CREATE TABLE IF NOT EXISTS `watchlist_items` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `watchlist_id` bigint NOT NULL COMMENT '自选分组 ID',
    `symbol` varchar(16) NOT NULL COMMENT '股票代码',
    `note` varchar(255) DEFAULT NULL COMMENT '备注',
    `alert_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启提醒',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_watchlist_item_symbol` (`watchlist_id`, `symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自选股条目表';

CREATE TABLE IF NOT EXISTS `paper_accounts` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `account_no` varchar(64) NOT NULL COMMENT '模拟账户号',
    `cash_balance` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '可用现金',
    `frozen_cash` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '冻结现金',
    `total_asset` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '总资产',
    `total_pnl` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '累计盈亏',
    `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_accounts_user` (`user_id`),
    UNIQUE KEY `uk_paper_accounts_no` (`account_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟账户表';

CREATE TABLE IF NOT EXISTS `paper_positions` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `account_id` bigint NOT NULL COMMENT '模拟账户 ID',
    `symbol` varchar(16) NOT NULL COMMENT '股票代码',
    `position_qty` int NOT NULL DEFAULT 0 COMMENT '持仓数量',
    `available_qty` int NOT NULL DEFAULT 0 COMMENT '可卖数量',
    `avg_cost` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '持仓成本',
    `market_value` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '市值',
    `floating_pnl` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '浮动盈亏',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_positions_symbol` (`account_id`, `symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟持仓表';

CREATE TABLE IF NOT EXISTS `paper_orders` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `account_id` bigint NOT NULL COMMENT '模拟账户 ID',
    `symbol` varchar(16) NOT NULL COMMENT '股票代码',
    `side` varchar(16) NOT NULL COMMENT '买卖方向',
    `order_type` varchar(16) NOT NULL DEFAULT 'market' COMMENT '委托类型',
    `order_price` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '委托价格',
    `order_qty` int NOT NULL DEFAULT 0 COMMENT '委托数量',
    `filled_qty` int NOT NULL DEFAULT 0 COMMENT '成交数量',
    `order_status` varchar(32) NOT NULL DEFAULT 'submitted' COMMENT '委托状态',
    `client_request_id` varchar(64) DEFAULT NULL COMMENT '客户端幂等号',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_orders_client_request` (`client_request_id`),
    KEY `idx_paper_orders_account` (`account_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟委托单表';

CREATE TABLE IF NOT EXISTS `paper_trades` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `order_id` bigint NOT NULL COMMENT '委托单 ID',
    `account_id` bigint NOT NULL COMMENT '模拟账户 ID',
    `symbol` varchar(16) NOT NULL COMMENT '股票代码',
    `side` varchar(16) NOT NULL COMMENT '买卖方向',
    `trade_price` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '成交价格',
    `trade_qty` int NOT NULL DEFAULT 0 COMMENT '成交数量',
    `trade_amount` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '成交金额',
    `trade_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成交时间',
    PRIMARY KEY (`id`),
    KEY `idx_paper_trades_account` (`account_id`, `trade_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟成交表';

CREATE TABLE IF NOT EXISTS `paper_daily_assets` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `account_id` bigint NOT NULL COMMENT '模拟账户 ID',
    `trade_date` date NOT NULL COMMENT '资产日期',
    `cash_balance` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '现金余额',
    `market_value` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '持仓市值',
    `total_asset` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '总资产',
    `daily_pnl` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '当日盈亏',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_daily_assets` (`account_id`, `trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟日资产快照表';

CREATE TABLE IF NOT EXISTS `ai_sessions` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `session_id` varchar(64) NOT NULL COMMENT '会话 ID',
    `context_type` varchar(32) NOT NULL DEFAULT 'general' COMMENT '上下文类型',
    `context_ref` varchar(128) DEFAULT NULL COMMENT '上下文引用',
    `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
    `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_sessions` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话主表';

CREATE TABLE IF NOT EXISTS `ai_usage_records` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户 ID',
    `feature_code` varchar(64) NOT NULL COMMENT '功能编码',
    `membership_level` varchar(32) NOT NULL COMMENT '会员等级',
    `trace_id` varchar(128) DEFAULT NULL COMMENT '追踪 ID',
    `request_tokens` int NOT NULL DEFAULT 0 COMMENT '请求 token 数',
    `response_tokens` int NOT NULL DEFAULT 0 COMMENT '响应 token 数',
    `status` varchar(32) NOT NULL DEFAULT 'success' COMMENT '调用状态',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_usage_user_feature` (`user_id`, `feature_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用消耗记录表';

CREATE TABLE IF NOT EXISTS `system_configs` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `config_key` varchar(64) NOT NULL COMMENT '配置键',
    `config_value` text COMMENT '配置值',
    `description` varchar(255) DEFAULT NULL COMMENT '配置说明',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_configs_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

INSERT INTO `membership_plans` (`plan_code`, `plan_name`, `price`, `billing_cycle`, `quota_json`, `status`)
SELECT 'free', '普通版', 0.00, 'monthly',
       '{"ai_chat_daily":20,"watchlist_count":1,"alert_count":3}', 'enabled'
WHERE NOT EXISTS (SELECT 1 FROM `membership_plans` WHERE `plan_code` = 'free');

INSERT INTO `membership_plans` (`plan_code`, `plan_name`, `price`, `billing_cycle`, `quota_json`, `status`)
SELECT 'vip', '会员版', 199.00, 'monthly',
       '{"ai_chat_daily":200,"watchlist_count":10,"alert_count":50}', 'enabled'
WHERE NOT EXISTS (SELECT 1 FROM `membership_plans` WHERE `plan_code` = 'vip');

INSERT INTO `sectors` (`sector_code`, `sector_name`, `parent_code`, `sort_order`)
SELECT 'consumer', '大消费', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM `sectors` WHERE `sector_code` = 'consumer');

INSERT INTO `sectors` (`sector_code`, `sector_name`, `parent_code`, `sort_order`)
SELECT 'technology', '科技成长', NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM `sectors` WHERE `sector_code` = 'technology');

INSERT INTO `sectors` (`sector_code`, `sector_name`, `parent_code`, `sort_order`)
SELECT 'finance', '金融地产', NULL, 3
WHERE NOT EXISTS (SELECT 1 FROM `sectors` WHERE `sector_code` = 'finance');

INSERT INTO `sectors` (`sector_code`, `sector_name`, `parent_code`, `sort_order`)
SELECT 'new_energy', '新能源', NULL, 4
WHERE NOT EXISTS (SELECT 1 FROM `sectors` WHERE `sector_code` = 'new_energy');

INSERT INTO `stocks` (`symbol`, `name`, `exchange`, `market`, `sector_code`, `status`)
SELECT '600519', '贵州茅台', 'SH', 'A', 'consumer', 'active'
WHERE NOT EXISTS (SELECT 1 FROM `stocks` WHERE `symbol` = '600519');

INSERT INTO `stocks` (`symbol`, `name`, `exchange`, `market`, `sector_code`, `status`)
SELECT '000001', '平安银行', 'SZ', 'A', 'finance', 'active'
WHERE NOT EXISTS (SELECT 1 FROM `stocks` WHERE `symbol` = '000001');

INSERT INTO `stocks` (`symbol`, `name`, `exchange`, `market`, `sector_code`, `status`)
SELECT '300750', '宁德时代', 'SZ', 'A', 'new_energy', 'active'
WHERE NOT EXISTS (SELECT 1 FROM `stocks` WHERE `symbol` = '300750');

INSERT INTO `stocks` (`symbol`, `name`, `exchange`, `market`, `sector_code`, `status`)
SELECT '600036', '招商银行', 'SH', 'A', 'finance', 'active'
WHERE NOT EXISTS (SELECT 1 FROM `stocks` WHERE `symbol` = '600036');

INSERT INTO `system_configs` (`config_key`, `config_value`, `description`)
SELECT 'paper_account_initial_cash', '1000000', '模拟账户初始资金'
WHERE NOT EXISTS (SELECT 1 FROM `system_configs` WHERE `config_key` = 'paper_account_initial_cash');

INSERT INTO `system_configs` (`config_key`, `config_value`, `description`)
SELECT 'market_quote_cache_ttl_seconds', '30', '行情缓存秒数'
WHERE NOT EXISTS (SELECT 1 FROM `system_configs` WHERE `config_key` = 'market_quote_cache_ttl_seconds');

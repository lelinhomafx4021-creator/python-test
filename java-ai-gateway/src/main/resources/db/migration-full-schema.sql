-- ============================================================
-- AI-Investor 完整建表 SQL（合并自 V1-V11 迁移文件）
-- 仅供审核参考，不要直接执行（Flyway 已经执行过了）
-- ============================================================

-- ============================================================
-- 1. 用户与认证
-- ============================================================

CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(255) DEFAULT NULL COMMENT 'BCrypt 密码摘要',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `nickname` varchar(64) DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `role` varchar(32) NOT NULL DEFAULT 'normal' COMMENT '用户角色：guest/normal/vip/admin',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=启用 0=禁用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `user_profiles` (
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `risk_level` varchar(32) DEFAULT 'balanced' COMMENT '风险等级',
  `investment_years` int DEFAULT 0 COMMENT '投资年限',
  `interested_sectors` varchar(255) DEFAULT NULL COMMENT '感兴趣板块，逗号分隔',
  `bio` varchar(255) DEFAULT NULL COMMENT '个性化简介',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

-- ============================================================
-- 2. 会员与配额
-- ============================================================

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

CREATE TABLE IF NOT EXISTS `vip_applications` (
  `id` bigint AUTO_INCREMENT PRIMARY KEY,
  `user_id` bigint NOT NULL COMMENT '申请用户ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `payment_amount` decimal(10,2) DEFAULT 199.00 COMMENT '支付金额',
  `payment_screenshot` varchar(512) DEFAULT '' COMMENT '付款截图URL',
  `payment_note` varchar(256) DEFAULT '' COMMENT '用户留言',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `reject_reason` varchar(256) DEFAULT '' COMMENT '拒绝原因',
  `reviewed_by` bigint DEFAULT NULL COMMENT '审核人ID',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (`status`),
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP申请审核表';

-- ============================================================
-- 3. 行情与自选
-- ============================================================

CREATE TABLE IF NOT EXISTS `stocks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `symbol` varchar(16) NOT NULL COMMENT '股票代码',
  `name` varchar(64) NOT NULL COMMENT '股票名称',
  `pinyin` varchar(64) DEFAULT NULL COMMENT '拼音首字母',
  `exchange` varchar(16) DEFAULT NULL COMMENT '交易所',
  `market` varchar(32) DEFAULT NULL COMMENT '市场',
  `sector_code` varchar(64) DEFAULT NULL COMMENT '板块编码',
  `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocks_symbol` (`symbol`),
  INDEX idx_stocks_pinyin (`pinyin`)
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

-- ============================================================
-- 4. 模拟交易
-- ============================================================

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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `paid_at` datetime DEFAULT NULL COMMENT '到账时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_cash_transfers_out_trade` (`out_trade_no`),
  KEY `idx_paper_cash_transfers_account` (`account_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟资金充值转账记录表';

CREATE TABLE IF NOT EXISTS `transaction_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型',
  `symbol` varchar(16) DEFAULT NULL COMMENT '股票代码',
  `side` varchar(8) DEFAULT NULL COMMENT '买卖方向',
  `quantity` int DEFAULT NULL COMMENT '数量',
  `price` decimal(18,4) DEFAULT NULL COMMENT '价格',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `balance_after` decimal(18,2) DEFAULT NULL COMMENT '操作后余额',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水日志表';

-- ============================================================
-- 5. AI 会话与工单
-- ============================================================

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

CREATE TABLE IF NOT EXISTS `ai_chat_turns` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天历史表';

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
  `process_note` text NULL COMMENT '管理员处理备注',
  `response_message` text NULL COMMENT '回复给用户的处理结果',
  `handled_by` varchar(64) NULL COMMENT '处理人用户名',
  `handled_at` datetime NULL COMMENT '处理时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_id` (`trace_id`),
  KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 人工兜底工单表';

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

-- ============================================================
-- 6. 通知与系统
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `category` varchar(32) NOT NULL DEFAULT 'system' COMMENT '通知分类',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` varchar(255) NOT NULL COMMENT '通知内容',
  `status` varchar(16) NOT NULL DEFAULT 'unread' COMMENT '读取状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL COMMENT '读取时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_notifications_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知记录表';

CREATE TABLE IF NOT EXISTS `system_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '配置说明',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_configs_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- 7. 系统公告（V12）
-- ============================================================

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

-- V10__Create_Vip_Applications_Table.sql
-- VIP 申请审核表：用户扫码付款后提交申请，管理员审核通过后升级角色

CREATE TABLE IF NOT EXISTS vip_applications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL COMMENT '申请用户ID',
    username        VARCHAR(64) NOT NULL COMMENT '用户名（冗余，方便展示）',
    payment_amount  DECIMAL(10,2) DEFAULT 199.00 COMMENT '支付金额',
    payment_screenshot VARCHAR(512) DEFAULT '' COMMENT '付款截图URL或路径',
    payment_note    VARCHAR(256) DEFAULT '' COMMENT '用户留言（如微信号、备注）',
    status          VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    reject_reason   VARCHAR(256) DEFAULT '' COMMENT '拒绝原因',
    reviewed_by     BIGINT DEFAULT NULL COMMENT '审核人ID',
    reviewed_at     DATETIME DEFAULT NULL COMMENT '审核时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP申请审核表';

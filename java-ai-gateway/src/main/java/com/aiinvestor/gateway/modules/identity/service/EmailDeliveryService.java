package com.aiinvestor.gateway.modules.identity.service;

/**
 * 邮件发送抽象，方便开发环境 mock 和生产环境 SMTP 切换。
 */
public interface EmailDeliveryService {

    void sendVerificationCode(String email, String code);
}

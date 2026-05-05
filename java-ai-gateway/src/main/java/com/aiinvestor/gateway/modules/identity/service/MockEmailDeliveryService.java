package com.aiinvestor.gateway.modules.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 开发环境邮件 mock：不真实发送，只打日志。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.email", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockEmailDeliveryService implements EmailDeliveryService {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("Mock email verification code -> email={}, code={}", email, code);
    }
}

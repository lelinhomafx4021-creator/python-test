package com.aiinvestor.gateway.modules.identity.service;

import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import org.springframework.mail.MailException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 邮箱验证码服务。
 */
@Service
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final String SCENE_REGISTER = "register";

    private final StringRedisTemplate stringRedisTemplate;
    private final EmailDeliveryService emailDeliveryService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(StringRedisTemplate stringRedisTemplate,
                                    EmailDeliveryService emailDeliveryService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.emailDeliveryService = emailDeliveryService;
    }

    public void sendRegisterCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey(normalizedEmail)))) {
            throw new BusinessException("验证码发送过于频繁，请 60 秒后重试");
        }

        String code = generateCode();
        stringRedisTemplate.opsForValue().set(codeKey(normalizedEmail), code, CODE_TTL);
        stringRedisTemplate.opsForValue().set(cooldownKey(normalizedEmail), "1", COOLDOWN_TTL);
        try {
            emailDeliveryService.sendVerificationCode(normalizedEmail, code);
        } catch (MailException ex) {
            stringRedisTemplate.delete(codeKey(normalizedEmail));
            stringRedisTemplate.delete(cooldownKey(normalizedEmail));
            throw new BusinessException("邮箱验证码发送失败，请检查发件邮箱 SMTP 配置后重试");
        }
    }

    public void verifyRegisterCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey(normalizedEmail));
        if (cachedCode == null || cachedCode.isBlank()) {
            throw new BusinessException("邮箱验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(code == null ? "" : code.trim())) {
            throw new BusinessException("邮箱验证码错误");
        }
        stringRedisTemplate.delete(codeKey(normalizedEmail));
    }

    private String generateCode() {
        int number = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(number);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String codeKey(String email) {
        return "email:code:" + SCENE_REGISTER + ":" + email;
    }

    private String cooldownKey(String email) {
        return "email:cooldown:" + SCENE_REGISTER + ":" + email;
    }
}

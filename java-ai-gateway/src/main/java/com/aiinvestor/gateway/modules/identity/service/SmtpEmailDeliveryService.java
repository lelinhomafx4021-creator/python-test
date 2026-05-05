package com.aiinvestor.gateway.modules.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.email", name = "mock-enabled", havingValue = "false")
public class SmtpEmailDeliveryService implements EmailDeliveryService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailDeliveryService(JavaMailSender mailSender,
                                    @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("AI Investor 注册验证码");
        message.setText("您的注册验证码为：" + code + "，5 分钟内有效，请勿泄露。");
        mailSender.send(message);
    }
}

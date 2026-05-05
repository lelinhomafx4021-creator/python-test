package com.aiinvestor.gateway.modules.identity.service;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserMapper;
import com.aiinvestor.gateway.modules.identity.dto.RegisterRequest;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final EmailVerificationService emailVerificationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, EmailVerificationService emailVerificationService) {
        this.userMapper = userMapper;
        this.emailVerificationService = emailVerificationService;
    }

    public UserDO getById(Long id) {
        return userMapper.selectById(id);
    }

    public UserDO getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, username)
                        .last("limit 1")
        );
    }

    public UserDO getByEmail(String email) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getEmail, normalizeEmail(email))
                        .last("limit 1")
        );
    }

    public UserDO validateLogin(String username, String rawPassword) {
        UserDO user = getByUsername(username);
        if (user == null) {
            return null;
        }
        if (!isUserActive(user.getStatus())) {
            return null;
        }

        String encodedPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
                ? user.getPasswordHash()
                : user.getPassword();
        if (encodedPassword == null || !passwordEncoder.matches(rawPassword, encodedPassword)) {
            return null;
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    public UserDO register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = normalizeEmail(request.getEmail());
        if (getByUsername(username) != null) {
            throw new BusinessException("用户名已存在，请更换后重试");
        }
        if (getByEmail(email) != null) {
            throw new BusinessException("该邮箱已注册，请更换后重试");
        }
        emailVerificationService.verifyRegisterCode(email, request.getEmailCode());

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPassword(user.getPasswordHash());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        user.setEmail(email);
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank() ? username : request.getNickname().trim());
        user.setRole("normal");
        user.setStatus("1");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public void sendRegisterEmailCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (getByEmail(normalizedEmail) != null) {
            throw new BusinessException("该邮箱已注册，请直接登录或更换邮箱");
        }
        emailVerificationService.sendRegisterCode(normalizedEmail);
    }

    private boolean isUserActive(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim();
        return "1".equals(normalized) || "active".equalsIgnoreCase(normalized);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}

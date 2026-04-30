package com.aiinvestor.gateway.service;

import com.aiinvestor.gateway.dao.entity.UserDO;
import com.aiinvestor.gateway.dao.mapper.UserMapper;
import com.aiinvestor.gateway.dto.RegisterRequest;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务。
 * 继续负责登录校验，同时把“最后登录时间”这类主业务信息沉淀到用户主档里。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 按主键查询用户。
     */
    public UserDO getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 按用户名查询用户。
     */
    public UserDO getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, username)
                        .last("limit 1")
        );
    }

    /**
     * 登录校验。
     * 兼容旧 password 字段与新 passwordHash 字段，方便平滑升级。
     */
    public UserDO validateLogin(String username, String rawPassword) {
        UserDO user = getByUsername(username);
        if (user == null) {
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
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

    /**
     * 注册新用户。
     */
    public UserDO register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (getByUsername(username) != null) {
            throw new BusinessException("用户名已存在，请更换后重试");
        }

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPassword(user.getPasswordHash());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank() ? username : request.getNickname().trim());
        user.setRole("normal");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }
}

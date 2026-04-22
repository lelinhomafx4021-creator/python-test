package com.aiinvestor.gateway.service;

import com.aiinvestor.gateway.dao.entity.UserDO;
import com.aiinvestor.gateway.dao.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户业务类。
 * 面试加分：为什么不仅用 Mapper 还要写 Service？
 * 因为 Service 层负责业务逻辑（比如：校验、加密、多表关联），Mapper 只管 SQL。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 根据主键 ID 获取用户信息
     */
    public UserDO getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 判断用户是否有效且存在
     */
    public boolean isValidUser(String userId) {
        try {
            Long id = Long.parseLong(userId);
            UserDO user = userMapper.selectById(id);
            // 用户存在且状态为 1（正常）才视为有效
            return user != null && user.getStatus() == 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

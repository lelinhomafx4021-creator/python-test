package com.aiinvestor.gateway.modules.identity.service;

import com.aiinvestor.gateway.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.vo.UserProfileVO;
import org.springframework.stereotype.Service;

/**
 * 身份域服务。
 * 负责把底层用户实体转换成面向终端展示的用户资料对象。
 */
@Service
public class IdentityService {

    /**
     * 构建当前用户资料。
     */
    public UserProfileVO buildProfile(UserDO user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getLastLoginAt()
        );
    }
}

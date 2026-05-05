package com.aiinvestor.gateway.modules.identity.service;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserMapper;
import com.aiinvestor.gateway.modules.identity.dao.entity.UserProfileDO;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserProfileMapper;
import com.aiinvestor.gateway.modules.identity.dto.UpdateUserProfileRequest;
import com.aiinvestor.gateway.modules.identity.vo.UserProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 身份与个人中心服务。
 */
@Service
public class IdentityService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    public IdentityService(UserMapper userMapper, UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    /**
     * 构建当前用户资料。
     */
    public UserProfileVO buildProfile(UserDO user) {
        UserProfileDO profile = userProfileMapper.selectById(user.getId());
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getLastLoginAt(),
                profile == null ? "balanced" : profile.getRiskLevel(),
                profile == null ? 0 : profile.getInvestmentYears(),
                profile == null ? "" : profile.getInterestedSectors(),
                profile == null ? "" : profile.getBio()
        );
    }

    /**
     * 更新个人中心资料。
     */
    @Transactional
    public UserProfileVO updateProfile(UserDO user, UpdateUserProfileRequest request) {
        user.setNickname(request.getNickname().trim());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        UserProfileDO profile = userProfileMapper.selectById(user.getId());
        if (profile == null) {
            profile = new UserProfileDO();
            profile.setUserId(user.getId());
        }
        profile.setRiskLevel(isBlank(request.getRiskLevel()) ? "balanced" : request.getRiskLevel().trim());
        profile.setInvestmentYears(request.getInvestmentYears() == null ? 0 : request.getInvestmentYears());
        profile.setInterestedSectors(isBlank(request.getInterestedSectors()) ? "" : request.getInterestedSectors().trim());
        profile.setBio(isBlank(request.getBio()) ? "" : request.getBio().trim());
        profile.setUpdatedAt(LocalDateTime.now());

        if (userProfileMapper.selectById(user.getId()) == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }

        return buildProfile(userMapper.selectById(user.getId()));
    }

    /**
     * 更新头像地址。
     */
    @Transactional
    public UserProfileVO updateAvatar(UserDO user, String avatarUrl) {
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return buildProfile(userMapper.selectById(user.getId()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

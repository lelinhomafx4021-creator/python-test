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
 * <p>
 * 负责用户资料查询、更新、头像管理等核心业务逻辑。
 * 操作涉及两张表：
 * <ul>
 *   <li>users 主表 — 用户名、昵称、头像、手机号等身份信息</li>
 *   <li>user_profiles 扩展画像表 — 风险等级、投资年限、关注板块等投资偏好</li>
 * </ul>
 * <p>
 * 更新方法均标注 @Transactional，确保两张表的写入在同一事务中，
 * 防止出现主表更新了但画像表没更新的数据不一致问题。
 *
 * @author AI Investor Team
 */
@Service
public class IdentityService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    /**
     * 构造器注入（Spring 推荐方式）。
     *
     * @param userMapper        用户主表 Mapper
     * @param userProfileMapper 用户扩展画像 Mapper
     */
    public IdentityService(UserMapper userMapper, UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    /**
     * 根据用户主表记录构建完整的个人中心资料 VO。
     * <p>
     * 逻辑：
     * <ol>
     *   <li>从 user_profiles 表查询该用户的扩展画像</li>
     *   <li>如果画像不存在，使用默认值填充（风险等级 balanced、投资年限 0 等）</li>
     *   <li>将主表字段与画像字段合并为 UserProfileVO 返回</li>
     * </ol>
     *
     * @param user 用户主表实体，不能为 null
     * @return 聚合了主表与画像表的完整用户资料 VO，画像缺失时用默认值填充
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
     * 更新个人中心资料（昵称、手机号、风险等级、投资年限等）。
     * <p>
     * 事务内执行以下步骤：
     * <ol>
     *   <li>更新 users 表的昵称、手机号</li>
     *   <li>查询 user_profiles 表是否存在该用户的画像记录</li>
     *   <li>不存在则创建新记录（INSERT），存在则更新（UPDATE）</li>
     *   <li>返回更新后的完整用户资料</li>
     * </ol>
     * <p>
     * 注意：前端提交的空字符串会被处理为 null（手机号）或默认值（风险等级）。
     *
     * @param user    当前登录用户实体
     * @param request 前端提交的更新请求，昵称必填，其余可选
     * @return 更新后的完整用户资料 VO
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
     * 更新用户头像地址。
     * <p>
     * 头像文件的上传由 AliyunOssService 负责（上传到 OSS 并返回 URL），
     * 本方法只负责将 OSS 返回的 URL 写入 users 表的 avatar_url 字段。
     *
     * @param user      当前登录用户实体
     * @param avatarUrl 阿里云 OSS 返回的头像图片 URL
     * @return 更新后的完整用户资料 VO
     */
    @Transactional
    public UserProfileVO updateAvatar(UserDO user, String avatarUrl) {
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return buildProfile(userMapper.selectById(user.getId()));
    }

    /**
     * 判断字符串是否为 null 或空串（包括只含空白字符的串）。
     *
     * @param value 待检查的字符串
     * @return true 表示为空或仅含空白字符
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

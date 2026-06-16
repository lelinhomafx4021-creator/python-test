package com.aiinvestor.gateway.modules.identity.dao.mapper;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserProfileDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户扩展画像（user_profiles 表）的 MyBatis-Plus Mapper 接口。
 * <p>
 * 继承 BaseMapper&lt;UserProfileDO&gt; 获得全套 CRUD 能力：
 * <ul>
 *   <li>selectById(userId)        — 按用户 ID 查询画像</li>
 *   <li>insert(profile)           — 首次创建画像记录</li>
 *   <li>updateById(profile)       — 更新已有画像</li>
 *   <li>deleteById(userId)        — 删除画像（极少使用）</li>
 * </ul>
 * <p>
 * 与 UserMapper 的分工：
 * UserMapper 管理用户主表（身份、登录），本 Mapper 管理投资偏好扩展信息。
 * 两张表通过 user_id 一对一关联。
 *
 * @author AI Investor Team
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileDO> {
}

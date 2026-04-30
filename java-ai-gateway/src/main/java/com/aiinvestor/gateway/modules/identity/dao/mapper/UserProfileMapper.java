package com.aiinvestor.gateway.modules.identity.dao.mapper;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserProfileDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户扩展画像 Mapper。
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileDO> {
}

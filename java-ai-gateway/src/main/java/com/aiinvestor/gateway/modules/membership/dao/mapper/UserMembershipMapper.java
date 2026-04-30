package com.aiinvestor.gateway.modules.membership.dao.mapper;

import com.aiinvestor.gateway.modules.membership.dao.entity.UserMembershipDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户会员关系 Mapper。
 */
@Mapper
public interface UserMembershipMapper extends BaseMapper<UserMembershipDO> {
}

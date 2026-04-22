package com.aiinvestor.gateway.dao.mapper;

import com.aiinvestor.gateway.dao.entity.UserDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口。
 * 继承 BaseMapper 即可获得全套 CRUD 能力，这就是 MyBatis-Plus 的魅力。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}

package com.aiinvestor.gateway.dao.mapper;

import com.aiinvestor.gateway.dao.entity.UserNotificationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通知记录 Mapper。
 */
@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotificationDO> {
}

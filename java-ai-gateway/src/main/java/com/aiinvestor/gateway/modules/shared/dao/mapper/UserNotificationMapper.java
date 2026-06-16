package com.aiinvestor.gateway.modules.shared.dao.mapper;

import com.aiinvestor.gateway.modules.shared.dao.entity.UserNotificationDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通知记录 Mapper。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动获得 CRUD 能力。
 * 复杂查询通过 LambdaQueryWrapper 在 Service 层构建。
 */
@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotificationDO> {
}

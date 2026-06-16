package com.aiinvestor.gateway.modules.ops.dao.mapper;

import com.aiinvestor.gateway.modules.ops.dao.entity.SystemConfigDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper。
 * <p>
 * 继承 MyBatis-Plus BaseMapper，提供 system_configs 表的基础 CRUD。
 * 按配置键查询时，通过 LambdaQueryWrapper 在 Service 层构建条件。
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigDO> {
}

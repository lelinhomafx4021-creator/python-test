package com.aiinvestor.gateway.modules.shared.dao.mapper;

import com.aiinvestor.gateway.modules.shared.dao.entity.AnnouncementDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统公告 Mapper。
 * <p>
 * 继承 MyBatis-Plus BaseMapper，提供公告表的基础 CRUD 操作。
 * 筛选条件（状态过滤、排序等）在 Service 层通过 LambdaQueryWrapper 构建。
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<AnnouncementDO> {
}

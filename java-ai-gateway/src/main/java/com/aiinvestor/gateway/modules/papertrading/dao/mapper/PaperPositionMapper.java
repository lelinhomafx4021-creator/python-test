package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperPositionDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟持仓 Mapper。
 * <p>
 * 对应 paper_positions 表，用于管理用户模拟账户的股票持仓数据。
 * 支持查询持仓明细、更新持仓数量和均价等操作。
 * 继承 MyBatis-Plus BaseMapper 获得标准 CRUD 能力。
 * </p>
 */
@Mapper
public interface PaperPositionMapper extends BaseMapper<PaperPositionDO> {
}

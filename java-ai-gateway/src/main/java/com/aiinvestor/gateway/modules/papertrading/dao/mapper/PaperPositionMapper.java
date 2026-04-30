package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperPositionDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟持仓 Mapper。
 */
@Mapper
public interface PaperPositionMapper extends BaseMapper<PaperPositionDO> {
}

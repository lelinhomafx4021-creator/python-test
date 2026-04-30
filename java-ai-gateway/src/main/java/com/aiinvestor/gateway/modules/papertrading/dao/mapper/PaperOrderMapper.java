package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperOrderDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟委托 Mapper。
 */
@Mapper
public interface PaperOrderMapper extends BaseMapper<PaperOrderDO> {
}

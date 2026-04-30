package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperTradeDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟成交 Mapper。
 */
@Mapper
public interface PaperTradeMapper extends BaseMapper<PaperTradeDO> {
}

package com.aiinvestor.gateway.modules.market.dao.mapper;

import com.aiinvestor.gateway.modules.market.dao.entity.StockDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 股票主数据 Mapper。
 */
@Mapper
public interface StockMapper extends BaseMapper<StockDO> {
}

package com.aiinvestor.gateway.modules.watchlist.dao.mapper;

import com.aiinvestor.gateway.modules.watchlist.dao.entity.WatchlistItemDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自选股条目 Mapper。
 */
@Mapper
public interface WatchlistItemMapper extends BaseMapper<WatchlistItemDO> {
}

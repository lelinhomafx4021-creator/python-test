package com.aiinvestor.gateway.modules.watchlist.dao.mapper;

import com.aiinvestor.gateway.modules.watchlist.dao.entity.WatchlistDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自选分组 Mapper。
 */
@Mapper
public interface WatchlistMapper extends BaseMapper<WatchlistDO> {
}

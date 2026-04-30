package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperDailyAssetDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟日资产快照 Mapper。
 */
@Mapper
public interface PaperDailyAssetMapper extends BaseMapper<PaperDailyAssetDO> {
}

package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperDailyAssetDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟账户每日资产快照 Mapper。
 * <p>
 * 对应 paper_daily_assets 表，用于存取每个交易日结算后的账户资产汇总数据。
 * 支持资金曲线绘制和日收益分析。
 * 继承 MyBatis-Plus BaseMapper 获得标准 CRUD 能力。
 * </p>
 */
@Mapper
public interface PaperDailyAssetMapper extends BaseMapper<PaperDailyAssetDO> {
}

package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperTradeDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟成交记录 Mapper。
 * <p>
 * 对应 paper_trades 表，用于管理模拟交易成交明细。
 * 每笔委托撮合成交后写入一条成交记录，用于交易历史查询和统计分析。
 * 继承 MyBatis-Plus BaseMapper 获得标准 CRUD 能力。
 * </p>
 */
@Mapper
public interface PaperTradeMapper extends BaseMapper<PaperTradeDO> {
}

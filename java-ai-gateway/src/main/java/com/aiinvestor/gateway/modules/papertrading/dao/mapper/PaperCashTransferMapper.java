package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperCashTransferDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟资金划转记录 Mapper。
 * <p>
 * 对应 paper_cash_transfers 表，用于管理用户模拟账户的充值/提现流水记录。
 * 继承 MyBatis-Plus BaseMapper 获得标准 CRUD 能力。
 * </p>
 */
@Mapper
public interface PaperCashTransferMapper extends BaseMapper<PaperCashTransferDO> {
}

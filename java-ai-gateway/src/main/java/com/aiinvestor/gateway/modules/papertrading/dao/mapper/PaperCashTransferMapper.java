package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperCashTransferDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟资金充值转账记录 Mapper。
 */
@Mapper
public interface PaperCashTransferMapper extends BaseMapper<PaperCashTransferDO> {
}

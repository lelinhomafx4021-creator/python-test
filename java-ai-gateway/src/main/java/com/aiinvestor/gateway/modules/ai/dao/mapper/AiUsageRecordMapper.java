package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiUsageRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消耗记录 Mapper。
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecordDO> {
}

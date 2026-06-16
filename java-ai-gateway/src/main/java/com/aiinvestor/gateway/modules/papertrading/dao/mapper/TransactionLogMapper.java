package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.TransactionLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易流水日志表 Mapper 接口。
 * <p>
 * 对应 transaction_logs 表，由 MQ 消费者异步写入，Controller 层只读查询。
 * 流水记录涵盖下单、成交、撤单、充值、提现等各类交易事件，用于交易历史展示和审计追溯。
 * 继承 MyBatis-Plus BaseMapper，标准 CRUD 操作由框架自动生成。
 * </p>
 *
 * @author AI Investor Team
 */
@Mapper
public interface TransactionLogMapper extends BaseMapper<TransactionLogDO> {
}

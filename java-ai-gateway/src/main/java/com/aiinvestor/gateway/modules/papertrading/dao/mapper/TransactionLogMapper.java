package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.TransactionLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ============================================================
 * 交易流水日志表 Mapper 接口
 * ============================================================
 *
 * 继承 BaseMapper，标准 CRUD 操作由 MyBatis-Plus 自动生成。
 *
 * @author AI Investor Team
 */
@Mapper
public interface TransactionLogMapper extends BaseMapper<TransactionLogDO> {
}

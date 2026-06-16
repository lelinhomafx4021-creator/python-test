package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperAccountDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟交易账户 Mapper。
 * <p>
 * 对应 papre_accounts 表，继承 MyBatis-Plus BaseMapper 获得标准 CRUD 能力。
 * 用于查询、创建和更新用户的模拟交易账户。
 * </p>
 */
@Mapper
public interface PaperAccountMapper extends BaseMapper<PaperAccountDO> {
}

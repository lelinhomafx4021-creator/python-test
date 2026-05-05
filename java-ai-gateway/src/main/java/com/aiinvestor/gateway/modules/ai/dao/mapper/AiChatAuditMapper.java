package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiChatAuditDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ============================================================
 * 审计流水表 Mapper 接口
 * ============================================================
 *
 * 继承 BaseMapper 即可，审计记录的 CRUD 都是标准操作，
 * 不需要自定义 SQL。
 *
 * @author AI Investor Team
 */
@Mapper
public interface AiChatAuditMapper extends BaseMapper<AiChatAuditDO> {
}

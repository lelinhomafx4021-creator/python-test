package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiUsageRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消耗记录 Mapper。
 *
 * <p>对应数据库表 ai_usage_records，提供用量记录的标准 CRUD 操作。
 * 继承 BaseMapper 即可，所有操作通过 MyBatis-Plus 的内置方法完成，
 * 无需自定义 SQL。
 *
 * <p>使用场景：
 * <ul>
 *   <li>插入：每次 AI 调用完成后异步写入一条消耗记录</li>
 *   <li>更新：异步回填 requestTokens / responseTokens 真实数据</li>
 *   <li>查询：按用户、时间段、功能编码统计 Token 消耗量</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecordDO> {
}

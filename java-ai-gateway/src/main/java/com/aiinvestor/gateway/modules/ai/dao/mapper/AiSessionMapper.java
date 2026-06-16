package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiSessionDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * AI 会话 Mapper。
 *
 * <p>对应数据库表 ai_sessions，提供会话级别的数据操作。
 * 除标准 CRUD 外，提供异步更新标题的自定义 SQL。
 *
 * <p>会话标题的生成时机：用户发起会话的第一轮对话后，
 * AI 异步分析对话内容并生成标题（如"茅台2024年财报分析"），
 * 然后通过 updateTitle 回填到会话记录中。
 *
 * @author AI Investor Team
 */
@Mapper
public interface AiSessionMapper extends BaseMapper<AiSessionDO> {

    /**
     * 异步更新会话标题（由 AI 生成）。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param title     AI 生成的新标题
     * @return 受影响的行数
     */
    @Update("UPDATE ai_sessions SET title = #{title} WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int updateTitle(@Param("userId") Long userId, @Param("sessionId") String sessionId, @Param("title") String title);
}

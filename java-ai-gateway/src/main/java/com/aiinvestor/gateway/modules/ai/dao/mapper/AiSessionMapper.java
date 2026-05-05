package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiSessionDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * AI 会话 Mapper。
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

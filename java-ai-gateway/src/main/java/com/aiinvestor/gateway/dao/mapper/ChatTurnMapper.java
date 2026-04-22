package com.aiinvestor.gateway.dao.mapper;

import com.aiinvestor.gateway.dao.entity.ChatTurnDO;
import com.aiinvestor.gateway.model.vo.ChatSessionSummaryVO;
import com.aiinvestor.gateway.model.vo.ChatTurnVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatTurnMapper extends BaseMapper<ChatTurnDO> {

    @Select("SELECT session_id, MAX(thread_id) as thread_id, MAX(title) as title, COUNT(*) as turn_count, MAX(created_at) as last_at " +
            "FROM ai_chat_turns " +
            "WHERE user_id = #{userId} AND session_id IS NOT NULL AND session_id <> '' " +
            "GROUP BY session_id " +
            "ORDER BY last_at DESC")
    List<ChatSessionSummaryVO> listSessionSummaries(@Param("userId") String userId);

    @Update("UPDATE ai_chat_turns SET title = #{title} WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int updateTitle(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("title") String title);

    @Select("SELECT COUNT(*) FROM ai_chat_turns WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int countBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    @Update("UPDATE ai_chat_turns SET answer = #{answer} WHERE trace_id = #{traceId}")
    int updateAnswerByTraceId(@Param("traceId") String traceId, @Param("answer") String answer);

    @Select("SELECT id, trace_id, query, answer, intent, source, review_passed, " +
            "response_mode, a2a_count, created_at " +
            "FROM ai_chat_turns " +
            "WHERE user_id = #{userId} AND session_id = #{sessionId} " +
            "ORDER BY id ASC " +
            "LIMIT #{offset}, #{limit}")
    List<ChatTurnVO> listTurnDetails(@Param("userId") String userId, 
                                     @Param("sessionId") String sessionId, 
                                     @Param("limit") int limit, 
                                     @Param("offset") int offset);
}

package com.aiinvestor.gateway.dao.mapper;

import com.aiinvestor.gateway.dao.entity.AiHandoffTicketDO;
import com.aiinvestor.gateway.model.vo.AiHandoffTicketVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 人工兜底工单 Mapper。
 */
@Mapper
public interface AiHandoffTicketMapper extends BaseMapper<AiHandoffTicketDO> {

    /** 按 traceId 判断当前请求是否已经建过工单。 */
    @Select("SELECT COUNT(*) FROM ai_handoff_tickets WHERE trace_id = #{traceId}")
    int countByTraceId(@Param("traceId") String traceId);

    /** 查询当前用户的人工兜底工单列表，按建单时间倒序。 */
    @Select("SELECT trace_id, session_id, query, handoff_reason, handoff_summary, status, created_at " +
            "FROM ai_handoff_tickets " +
            "WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC")
    List<AiHandoffTicketVO> listByUserId(@Param("userId") String userId);
}

package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.AiHandoffTicketDO;
import com.aiinvestor.gateway.modules.ai.vo.HandoffTicketVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 人工兜底工单 Mapper。
 *
 * <p>对应数据库表 ai_handoff_tickets，负责 AI 转人工工单的数据库操作。
 * 当用户在 AI 对话中说"转人工"或系统检测到回答质量不满足阈值时，
 * 自动创建工单，由人工客服介入处理。
 *
 * <p>核心方法：
 * <ul>
 *   <li>countByTraceId：幂等性检查，防止同一请求重复建单</li>
 *   <li>listByUserId：查询用户的所有工单，按建单时间倒序展示</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@Mapper
public interface AiHandoffTicketMapper extends BaseMapper<AiHandoffTicketDO> {

    /**
     * 按 traceId 判断当前请求是否已经建过工单。
     *
     * <p>这是幂等性保证的关键：traceId 是每次请求的唯一标识，
     * 如果已经存在同 traceId 的工单，说明该请求已被处理过，不需重复建单。
     *
     * @param traceId 请求追踪 ID（每次 AI 请求唯一）
     * @return 已存在的工单数量（0 表示未建单，>0 表示已存在）
     */
    @Select("SELECT COUNT(*) FROM ai_handoff_tickets WHERE trace_id = #{traceId}")
    int countByTraceId(@Param("traceId") String traceId);

    /**
     * 查询当前用户的人工兜底工单列表，按建单时间倒序。
     *
     * <p>仅查询用户端需要展示的字段，不包含 thread_id 等内部字段。
     * 返回的是 HandoffTicketVO 而非 DO，精简了接口响应体积。
     *
     * @param userId 用户 ID
     * @return 该用户的所有工单列表（按 created_at 降序，最新的在前）
     */
    @Select("SELECT trace_id, session_id, query, handoff_reason, handoff_summary, status, process_note, response_message, handled_by, handled_at, created_at " +
            "FROM ai_handoff_tickets " +
            "WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC")
    List<HandoffTicketVO> listByUserId(@Param("userId") Long userId);
}

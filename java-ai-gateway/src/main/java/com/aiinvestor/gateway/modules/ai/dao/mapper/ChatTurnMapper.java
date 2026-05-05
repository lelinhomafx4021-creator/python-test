package com.aiinvestor.gateway.modules.ai.dao.mapper;

import com.aiinvestor.gateway.modules.ai.dao.entity.ChatTurnDO;
import com.aiinvestor.gateway.modules.ai.vo.ChatSessionSummaryVO;
import com.aiinvestor.gateway.modules.ai.vo.ChatTurnVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * ============================================================
 * 聊天记录表 Mapper 接口
 * ============================================================
 *
 * 除了继承 BaseMapper 的基础 CRUD 外，
 * 这里定义了 4 个自定义 SQL 方法，用于复杂的业务查询。
 *
 * 面试要点（为什么写原生 SQL 而不是用 MyBatis-Plus 的 QueryWrapper？）：
 *   1. GROUP BY + 聚合函数（COUNT/MAX）：QueryWrapper 不擅长
 *   2. 性能优化：手写 SQL 可以精确控制 SQL 执行计划
 *   3. 可读性：复杂查询的 SQL 比嵌套 Lambda 更直观
 *
 * @author AI Investor Team
 */
@Mapper
public interface ChatTurnMapper extends BaseMapper<ChatTurnDO> {

    /**
     * 查询用户的会话摘要列表（左侧边栏）。
     *
     * SQL 解读：
     *   SELECT session_id, MAX(title), COUNT(*), MAX(created_at)
     *   GROUP BY session_id
     *   ORDER BY last_at DESC
     *
     * 聚合逻辑全在数据库完成，Java 端拿到的就是最终结果。
     * 注意：session_id 可能为空字符串，需要过滤掉。
     *
     * @param userId 用户 ID
     * @return 按最后活跃时间降序排列的会话列表
     */
    @Select("SELECT t.session_id, MAX(t.thread_id) as thread_id, s.title, COUNT(*) as turn_count, MAX(t.created_at) as last_at " +
            "FROM ai_chat_turns t " +
            "LEFT JOIN ai_sessions s ON t.session_id = s.session_id AND t.user_id = s.user_id " +
            "WHERE t.user_id = #{userId} AND t.session_id IS NOT NULL AND t.session_id <> '' " +
            "GROUP BY t.session_id, s.title " +
            "ORDER BY last_at DESC")
    List<ChatSessionSummaryVO> listSessionSummaries(@Param("userId") Long userId);

    /**
     * 统计某个会话下的消息数量。
     * 用于判断是否是该会话的第一轮对话（只有第一轮才触发标题生成）。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 消息数量
     */
    @Select("SELECT COUNT(*) FROM ai_chat_turns WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int countBySession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 根据 traceId 回填 AI 的最终回答。
     *
     * 流程：
     *   1. 先插入一条 answer="[思考中...]" 的占位记录
     *   2. Python 流式返回 final_answer 时，用此方法回填真实答案
     *
     * @param traceId 追踪 ID（唯一标识一次请求）
     * @param answer  AI 的最终回答内容
     * @return 受影响的行数
     */
    @Update("UPDATE ai_chat_turns SET answer = #{answer} WHERE trace_id = #{traceId}")
    int updateAnswerByTraceId(@Param("traceId") String traceId, @Param("answer") String answer);

    /**
     * 分页查询某个会话下的聊天详情。
     *
     * SQL 解读：
     *   SELECT ... FROM ai_chat_turns
     *   WHERE user_id = ? AND session_id = ?
     *   ORDER BY id ASC        ← 按时间正序（先问的在前）
     *   LIMIT offset, limit    ← MySQL 风格的分页
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param limit     每页条数（1-200，已在 Service 层做了边界校验）
     * @param offset    偏移量
     * @return 该会话下的聊天记录列表
     */
    @Select("SELECT id, trace_id, query, answer, intent, source, review_passed, " +
            "response_mode, a2a_count, created_at " +
            "FROM ai_chat_turns " +
            "WHERE user_id = #{userId} AND session_id = #{sessionId} " +
            "ORDER BY id ASC " +
            "LIMIT #{offset}, #{limit}")
    List<ChatTurnVO> listTurnDetails(@Param("userId") Long userId,
                                     @Param("sessionId") String sessionId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);
}

package com.aiinvestor.gateway.modules.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用消耗记录实体。
 *
 * <p>对应数据库表 ai_usage_records，用于记录每次 AI 调用的资源消耗与计费信息。
 * 该表为异步写入，不阻塞主业务流程。
 *
 * <p>业务意义：
 * <ul>
 *   <li>用量统计：按用户、功能、会员等级统计 Token 消耗和调用次数</li>
 *   <li>计费依据：不同会员等级有不同配额，用量记录是超限判断的数据来源</li>
 *   <li>成本核算：追踪 LLM API 的成本，便于运维和财务分析</li>
 * </ul>
 *
 * <p>token 字段说明：
 *   requestTokens 和 responseTokens 记录的是请求/响应消耗的 Token 数量，
 *   初始插入时为 0，后续通过异步回调或定时任务回填真实数据。
 *
 * @author AI Investor Team
 */
@Data
@TableName("ai_usage_records")
public class AiUsageRecordDO {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，关联 users 表 */
    private Long userId;

    /** 功能编码（如 ai_chat_stream / ai_title_gen），标识调用的是哪个 AI 功能 */
    private String featureCode;

    /** 会员等级（free / vip / svip），用于配额判断和分层统计 */
    private String membershipLevel;

    /** 全链路追踪 ID，关联本次调用的所有日志和审计记录 */
    private String traceId;

    /** 请求消耗的 Token 数（输入 token），实际值由异步回调回填 */
    private Integer requestTokens;

    /** 响应消耗的 Token 数（输出 token），实际值由异步回调回填 */
    private Integer responseTokens;

    /** 调用状态（success 成功 / failed 失败 / partial 部分成功） */
    private String status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}

package com.aiinvestor.gateway.modules.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用消耗记录实体。
 */
@Data
@TableName("ai_usage_records")
public class AiUsageRecordDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 功能编码。 */
    private String featureCode;

    /** 会员等级。 */
    private String membershipLevel;

    /** 追踪 ID。 */
    private String traceId;

    /** 请求 token 数。 */
    private Integer requestTokens;

    /** 响应 token 数。 */
    private Integer responseTokens;

    /** 状态。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

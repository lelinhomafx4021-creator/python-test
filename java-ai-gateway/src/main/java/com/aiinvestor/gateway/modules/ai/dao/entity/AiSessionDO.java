package com.aiinvestor.gateway.modules.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话主表实体。
 */
@Data
@TableName("ai_sessions")
public class AiSessionDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 会话 ID。 */
    private String sessionId;

    /** 上下文类型。 */
    private String contextType;

    /** 上下文引用。 */
    private String contextRef;

    /** 会话标题。 */
    private String title;

    /** 状态。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

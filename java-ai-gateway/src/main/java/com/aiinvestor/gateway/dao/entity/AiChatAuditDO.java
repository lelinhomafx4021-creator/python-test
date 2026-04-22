package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计流水实体。
 * 对应表 ai_chat_audits
 */
@Data
@Builder
@TableName("ai_chat_audits")
public class AiChatAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;
    private String userId;
    private String sessionId;
    private String endpoint;
    private String message;

    private LocalDateTime createdAt;
}

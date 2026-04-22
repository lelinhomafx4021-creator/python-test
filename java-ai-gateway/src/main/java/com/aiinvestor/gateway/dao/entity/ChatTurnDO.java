package com.aiinvestor.gateway.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天轮次实体（Java 业务侧主存储）。
 *
 * 说明：
 * - Python 不再存业务会话；会话和历史统一落 Java 库。
 * - 表结构对应 ai_chat_turns。
 */
@Data
@TableName("ai_chat_turns")
public class ChatTurnDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String sessionId;
    private String threadId;
    private String traceId;

    private String query;
    private String answer;
    private String intent;
    private String source;

    private Boolean reviewPassed;
    private String responseMode;
    private Integer a2aCount;
    private String title;

    private LocalDateTime createdAt;
}

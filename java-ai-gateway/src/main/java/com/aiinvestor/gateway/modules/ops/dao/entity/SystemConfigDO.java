package com.aiinvestor.gateway.modules.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体，对应数据库 system_configs 表。
 * <p>
 * 用于存储系统级别的动态配置项（如功能开关、阈值参数等），
 * 以键值对形式存储，支持运行时通过管理后台动态修改，
 * 避免将可变配置硬编码在 application.yml 中需要重启生效的问题。
 */
@Data
@TableName("system_configs")
public class SystemConfigDO {

    /** 主键，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键（唯一标识，如 ai.chat.max_tokens）。 */
    private String configKey;

    /** 配置值（统一以字符串存储，使用时按需转换类型）。 */
    private String configValue;

    /** 配置项说明（供管理员理解配置含义）。 */
    private String description;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}

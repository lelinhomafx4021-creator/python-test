package com.aiinvestor.gateway.modules.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体。
 */
@Data
@TableName("system_configs")
public class SystemConfigDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键。 */
    private String configKey;

    /** 配置值。 */
    private String configValue;

    /** 配置说明。 */
    private String description;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

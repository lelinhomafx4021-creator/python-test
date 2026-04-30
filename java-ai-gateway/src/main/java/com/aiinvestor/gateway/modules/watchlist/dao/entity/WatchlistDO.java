package com.aiinvestor.gateway.modules.watchlist.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自选分组实体。
 */
@Data
@TableName("watchlists")
public class WatchlistDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 分组名称。 */
    private String name;

    /** 是否默认分组。 */
    private Boolean isDefault;

    /** 排序。 */
    private Integer sortOrder;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

package com.aiinvestor.gateway.modules.watchlist.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自选股条目实体。
 */
@Data
@TableName("watchlist_items")
public class WatchlistItemDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分组 ID。 */
    private Long watchlistId;

    /** 股票代码。 */
    private String symbol;

    /** 备注。 */
    private String note;

    /** 是否开启提醒。 */
    private Boolean alertEnabled;

    /** 排序。 */
    private Integer sortOrder;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

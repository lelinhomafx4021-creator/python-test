package com.aiinvestor.gateway.modules.watchlist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自选分组视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistVO {

    /** 分组 ID。 */
    private Long id;

    /** 分组名称。 */
    private String name;

    /** 是否默认分组。 */
    private Boolean isDefault;

    /** 排序。 */
    private Integer sortOrder;

    /** 分组内股票列表。 */
    private List<WatchlistItemVO> items;
}

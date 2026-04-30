package com.aiinvestor.gateway.modules.market.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 板块视图对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectorVO {

    /** 板块编码。 */
    private String sectorCode;

    /** 板块名称。 */
    private String sectorName;

    /** 父级编码。 */
    private String parentCode;

    /** 排序。 */
    private Integer sortOrder;
}

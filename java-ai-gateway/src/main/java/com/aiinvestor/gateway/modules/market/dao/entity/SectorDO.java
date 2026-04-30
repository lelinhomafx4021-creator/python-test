package com.aiinvestor.gateway.modules.market.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 板块实体。
 */
@Data
@TableName("sectors")
public class SectorDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块编码。 */
    private String sectorCode;

    /** 板块名称。 */
    private String sectorName;

    /** 父级编码。 */
    private String parentCode;

    /** 排序。 */
    private Integer sortOrder;
}

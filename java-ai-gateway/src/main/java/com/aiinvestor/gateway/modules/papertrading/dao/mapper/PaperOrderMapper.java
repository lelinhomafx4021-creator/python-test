package com.aiinvestor.gateway.modules.papertrading.dao.mapper;

import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperOrderDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟委托订单 Mapper。
 * <p>
 * 对应 paper_orders 表，用于管理用户提交的模拟交易委托订单。
 * 支撑下单、撤单、查询委托列表等核心交易功能。
 * 继承 MyBatis-Plus BaseMapper 获得标准 CRUD 能力。
 * </p>
 */
@Mapper
public interface PaperOrderMapper extends BaseMapper<PaperOrderDO> {
}

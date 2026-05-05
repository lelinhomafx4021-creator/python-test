package com.aiinvestor.gateway.modules.identity.dao.mapper;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ============================================================
 * 用户表 Mapper 接口
 * ============================================================
 *
 * MyBatis-Plus 的核心设计：
 *   继承 BaseMapper<UserDO> 即可获得全套 CRUD 能力：
 *   - selectById(id)         : 按主键查询
 *   - selectList(wrapper)    : 条件查询
 *   - insert(entity)         : 插入
 *   - updateById(entity)     : 按主键更新
 *   - deleteById(id)         : 按主键删除
 *
 *   无需写任何 SQL 语句（除非有复杂查询），也不需要 XML 映射文件！
 *
 * @author AI Investor Team
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    // 所有基础 CRUD 由 BaseMapper 自动提供，无需额外定义
}

package com.aiinvestor.gateway.modules.identity.dao.mapper;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户主表（users）的 MyBatis-Plus Mapper 接口。
 * <p>
 * MyBatis-Plus 的核心设计：
 * 继承 BaseMapper&lt;UserDO&gt; 即可获得全套 CRUD 能力：
 * <ul>
 *   <li>selectById(id)       — 按主键查询用户</li>
 *   <li>selectList(wrapper)  — 条件查询用户列表</li>
 *   <li>insert(entity)       — 插入新用户</li>
 *   <li>updateById(entity)   — 按主键更新用户信息</li>
 *   <li>deleteById(id)       — 按主键删除用户</li>
 * </ul>
 * 无需写任何 SQL 语句（除非有复杂查询），也不需要 XML 映射文件。
 * <p>
 * 业务范围：用户登录验证、用户信息查询与更新、会员状态管理等场景。
 *
 * @author AI Investor Team
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    // 所有基础 CRUD 由 BaseMapper 自动提供，无需额外定义
}

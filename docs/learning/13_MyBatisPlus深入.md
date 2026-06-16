# 13 - MyBatis-Plus 深入

## 一、MyBatis-Plus 是什么

**一句话：MyBatis 的增强版，继承一个接口就自动获得 CRUD 能力，不用写 SQL。**

```
原生 MyBatis：
  写 Mapper 接口 → 写 XML 映射文件 → 写 SQL 语句
  查一个用户要写：select * from users where id = #{id}

MyBatis-Plus：
  继承 BaseMapper<UserDO> → 自动拥有 selectById、insert、updateById、deleteById
  一行 SQL 都不用写
```

## 二、三层结构

```
Controller → Service → Mapper → 数据库

WatchlistController.list()
  → WatchlistService.listWatchlists()
    → WatchlistMapper.selectList(wrapper)
      → 自动生成 SQL：SELECT * FROM watchlists WHERE user_id = ?
```

### 1. Entity（实体类）— 对应数据库表

```java
@Data                          // Lombok：自动生成 getter/setter
@TableName("users")            // 对应数据库的 users 表
public class UserDO {

    @TableId(type = IdType.AUTO)   // 主键，自增
    private Long id;

    private String username;       // 字段名和数据库列名自动映射
    private String password;       // 驼峰 → 下划线自动转换
    private String phone;
    private LocalDateTime createdAt; // created_at
}
```

**命名映射规则**（配置 `map-underline-to-camel-case`）：

```
Java 字段        数据库列
─────────        ────────
createdAt    →   created_at
userId       →   user_id
isDefault    →   is_default
```

### 2. Mapper（接口）— 继承 BaseMapper

```java
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    // 一行代码都不用写！
    // BaseMapper 自动提供 20+ 个 CRUD 方法
}
```

### 3. Service（业务层）— 调用 Mapper

```java
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserDO getByUsername(String username) {
        return userMapper.selectOne(
            new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username)
                .last("limit 1")
        );
    }
}
```

## 三、BaseMapper 自带的方法

```java
// ============ 查 ============
selectById(id)                    // SELECT * FROM users WHERE id = ?
selectByIds(ids)                  // SELECT * FROM users WHERE id IN (...)
selectOne(wrapper)                // SELECT * FROM users WHERE ... LIMIT 1
selectList(wrapper)               // SELECT * FROM users WHERE ...
selectCount(wrapper)              // SELECT COUNT(*) FROM users WHERE ...
selectPage(page, wrapper)         // 分页查询

// ============ 增 ============
insert(entity)                    // INSERT INTO users (...) VALUES (...)

// ============ 改 ============
updateById(entity)                // UPDATE users SET ... WHERE id = ?
update(entity, wrapper)           // UPDATE users SET ... WHERE ...

// ============ 删 ============
deleteById(id)                    // DELETE FROM users WHERE id = ?
deleteByIds(ids)                  // DELETE FROM users WHERE id IN (...)
delete(wrapper)                   // DELETE FROM users WHERE ...
```

## 四、LambdaQueryWrapper（条件构造器）

**核心作用：用 Java 代码代替 WHERE 条件。**

### 为什么用 Lambda 不用普通 Wrapper？

```java
// ❌ 普通 QueryWrapper（字符串写字段名）
new QueryWrapper<UserDO>()
    .eq("username", "zhangsan")     // "username" 写错了编译不报错
    .eq("statu", "active");          // 运行时才发现拼错了

// ✅ LambdaQueryWrapper（方法引用）
new LambdaQueryWrapper<UserDO>()
    .eq(UserDO::getUsername, "zhangsan")   // 编译期检查，写错直接报红
    .eq(UserDO::getStatus, "active");       // 安全！
```

**核心优势**：`UserDO::getUsername` 是方法引用，编译器会检查字段是否存在。

### 链式调用原理

```java
new LambdaQueryWrapper<UserDO>()
    .eq(UserDO::getUsername, "zhangsan")    // 返回 this
    .ne(UserDO::getStatus, "disabled")       // 返回 this
    .orderByDesc(UserDO::getCreatedAt);      // 返回 this

// 每个方法都返回 this，所以能一直 . 下去
```

### 三种写法

```java
// 写法 1：new（最常见）
new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, name);

// 写法 2：Wrappers 工具类（更简洁）
Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, name);

// 写法 3：先 new 再链式
LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(UserDO::getUsername, name);
```

**三种写法生成的 SQL 完全一样，性能也一样，只是语法糖。**

## 五、条件方法速查

```java
// ============ 比较 ============
.eq(字段, 值)          // =           WHERE username = 'zhangsan'
.ne(字段, 值)          // !=          WHERE status != 'disabled'
.gt(字段, 值)          // >           WHERE id > 100
.ge(字段, 值)          // >=          WHERE age >= 18
.lt(字段, 值)          // <           WHERE price < 100
.le(字段, 值)          // <=          WHERE stock <= 0

// ============ 模糊 ============
.like(字段, 值)        // %值%        WHERE name LIKE '%张%'
.likeLeft(字段, 值)    // %值         WHERE name LIKE '%三'
.likeRight(字段, 值)   // 值%         WHERE name LIKE '张%'

// ============ 范围 ============
.in(字段, 集合)        // IN          WHERE id IN (1, 2, 3)
.notIn(字段, 集合)     // NOT IN      WHERE id NOT IN (1, 2, 3)
.between(字段, a, b)   // BETWEEN     WHERE age BETWEEN 18 AND 30

// ============ 空值 ============
.isNull(字段)          // IS NULL     WHERE deleted_at IS NULL
.isNotNull(字段)       // IS NOT NULL WHERE email IS NOT NULL

// ============ 逻辑 ============
.and(w -> ...)         // AND (...)   复杂条件组合
.or()                  // OR          或条件

// ============ 排序 ============
.orderByAsc(字段)      // ORDER BY ... ASC
.orderByDesc(字段)     // ORDER BY ... DESC

// ============ 选择字段 ============
.select(字段1, 字段2)  // 只查某些字段

// ============ 追加 SQL ============
.last("LIMIT 1")       // 在 SQL 末尾追加（慎用）
```

## 六、实战示例

### 示例 1：登录查询

```java
// SELECT * FROM users WHERE username = 'zhangsan' LIMIT 1
userMapper.selectOne(
    Wrappers.<UserDO>lambdaQuery()
        .eq(UserDO::getUsername, "zhangsan")
        .last("limit 1")
);
```

### 示例 2：查某个用户的自选股，按排序号排列

```java
// SELECT * FROM watchlist_items WHERE watchlist_id = 5 ORDER BY sort_order ASC
watchlistItemMapper.selectList(
    Wrappers.<WatchlistItemDO>lambdaQuery()
        .eq(WatchlistItemDO::getWatchlistId, 5L)
        .orderByAsc(WatchlistItemDO::getSortOrder)
);
```

### 示例 3：IN 查询

```java
// SELECT * FROM watchlist_items WHERE watchlist_id IN (1, 2, 3)
watchlistItemMapper.selectList(
    Wrappers.<WatchlistItemDO>lambdaQuery()
        .in(WatchlistItemDO::getWatchlistId, watchlistIds)
);
```

### 示例 4：COUNT 查询

```java
// SELECT COUNT(*) FROM watchlists WHERE user_id = 1
int count = watchlistMapper.selectCount(
    Wrappers.<WatchlistDO>lambdaQuery()
        .eq(WatchlistDO::getUserId, userId)
).intValue();
```

### 示例 5：最近 7 天的订单

```java
// SELECT * FROM paper_orders WHERE user_id = 1 AND created_at BETWEEN ? AND ?
LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
orderMapper.selectList(
    Wrappers.<PaperOrderDO>lambdaQuery()
        .eq(PaperOrderDO::getUserId, userId)
        .between(PaperOrderDO::getCreatedAt, weekAgo, LocalDateTime.now())
        .orderByDesc(PaperOrderDO::getCreatedAt)
);
```

### 示例 6：OR 条件

```java
// SELECT * FROM users WHERE role = 'ADMIN' OR role = 'SUPER_ADMIN'
userMapper.selectList(
    Wrappers.<UserDO>lambdaQuery()
        .eq(UserDO::getRole, "ADMIN")
        .or()
        .eq(UserDO::getRole, "SUPER_ADMIN")
);

// 或者更简洁：
userMapper.selectList(
    Wrappers.<UserDO>lambdaQuery()
        .in(UserDO::getRole, List.of("ADMIN", "SUPER_ADMIN"))
);
```

### 示例 7：只查某些字段

```java
// SELECT id, username, role FROM users WHERE status = 'active'
userMapper.selectList(
    Wrappers.<UserDO>lambdaQuery()
        .eq(UserDO::getStatus, "active")
        .select(UserDO::getId, UserDO::getUsername, UserDO::getRole)
);
```

### 示例 8：AND 嵌套

```java
// SELECT * FROM orders WHERE status = 'active' AND (price > 100 OR vip = true)
orderMapper.selectList(
    Wrappers.<OrderDO>lambdaQuery()
        .eq(OrderDO::getStatus, "active")
        .and(w -> w
            .gt(OrderDO::getPrice, 100)
            .or()
            .eq(OrderDO::getIsVip, true)
        )
);
```

### 示例 9：模糊查询

```java
// SELECT * FROM users WHERE username LIKE '%zhang%'
userMapper.selectList(
    Wrappers.<UserDO>lambdaQuery()
        .like(UserDO::getUsername, "zhang")
);
```

### 示例 10：插入

```java
UserDO user = new UserDO();
user.setUsername("zhangsan");
user.setPassword("xxx");
userMapper.insert(user);
// INSERT INTO users (username, password) VALUES ('zhangsan', 'xxx')
// 插入后 user.id 自动回填
```

### 示例 11：更新

```java
user.setLastLoginAt(LocalDateTime.now());
userMapper.updateById(user);
// UPDATE users SET last_login_at = ? WHERE id = 1
// 只更新非 null 字段
```

### 示例 12：条件更新

```java
userMapper.update(null,
    Wrappers.<UserDO>lambdaUpdate()
        .eq(UserDO::getUsername, "zhangsan")
        .set(UserDO::getStatus, "disabled")
);
// UPDATE users SET status = 'disabled' WHERE username = 'zhangsan'
```

### 示例 13：删除

```java
userMapper.deleteById(1L);
// DELETE FROM users WHERE id = 1

userMapper.delete(
    Wrappers.<UserDO>lambdaQuery()
        .eq(UserDO::getStatus, "disabled")
);
// DELETE FROM users WHERE status = 'disabled'
```

## 七、更简单的方式：方法名自动生成 SQL

不需要写 Wrapper，**方法名就是 WHERE 条件**：

```java
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    // 方法名自动生成 SQL
    UserDO findByUsername(String username);
    // → SELECT * FROM users WHERE username = ?

    List<UserDO> findByRole(String role);
    // → SELECT * FROM users WHERE role = ?

    List<UserDO> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    // → SELECT * FROM users WHERE created_at BETWEEN ? AND ?

    int countByStatus(String status);
    // → SELECT COUNT(*) FROM users WHERE status = ?

    void deleteByUsername(String username);
    // → DELETE FROM users WHERE username = ?
}
```

### 命名规则

```
findBy + 字段名              → WHERE 字段 = ?
findBy + 字段名 + Between    → WHERE 字段 BETWEEN ? AND ?
findBy + 字段名 + Like       → WHERE 字段 LIKE ?
findBy + 字段名 + In        → WHERE 字段 IN (?)
findBy + 字段名 + IsNull    → WHERE 字段 IS NULL
findBy + 字段名 + OrderByX  → WHERE ... ORDER BY X

countBy + 字段名             → SELECT COUNT(*)
deleteBy + 字段名            → DELETE
existsBy + 字段名            → SELECT COUNT(*) > 0
```

### 对比

```java
// LambdaQueryWrapper（灵活，适合动态条件）
userMapper.selectOne(
    Wrappers.<UserDO>lambdaQuery()
        .eq(UserDO::getUsername, "zhangsan")
        .last("limit 1")
);

// 方法名（最简单，适合固定条件）
userMapper.findByUsername("zhangsan");
```

### 什么时候用哪个

| 场景 | 用什么 |
|------|--------|
| 简单查询（一个条件，固定不变） | 方法名命名 |
| 中等查询（动态条件、排序、分页） | LambdaQueryWrapper |
| 复杂查询（JOIN、子查询） | @Select 注解 或 XML 写 SQL |

## 八、MyBatis-Plus 的短板：多表联查

```java
// ❌ 多表联查写不出来
SELECT u.username, w.name, wi.symbol
FROM users u
JOIN watchlists w ON w.user_id = u.id
JOIN watchlist_items wi ON wi.watchlist_id = w.id
WHERE u.id = 1

// 没有任何 Wrapper 能写出 JOIN 语法
// 只能写原生 SQL
```

### 项目里的解决方式：多次单表查询 + Java 拼装

```java
// 第 1 步：查分组列表（单表）
List<WatchlistDO> watchlists = watchlistMapper.selectList(
    Wrappers.<WatchlistDO>lambdaQuery()
        .eq(WatchlistDO::getUserId, userId)
);

// 第 2 步：查所有分组的股票（单表 IN 查询）
List<Long> watchlistIds = watchlists.stream().map(WatchlistDO::getId).toList();
List<WatchlistItemDO> items = watchlistItemMapper.selectList(
    Wrappers.<WatchlistItemDO>lambdaQuery()
        .in(WatchlistItemDO::getWatchlistId, watchlistIds)
);

// 第 3 步：Java 代码里组装
Map<Long, List<WatchlistItemDO>> grouped = items.stream()
    .collect(Collectors.groupingBy(WatchlistItemDO::getWatchlistId));
```

**好处**：不用写 SQL，代码清晰。
**坏处**：两次查询，不如一次 JOIN 快（但数据量小无所谓）。

### 必须写 SQL 时怎么办

```java
// 方案 1：@Select 注解
@Select("SELECT u.username, w.name FROM users u JOIN watchlists w ON w.user_id = u.id WHERE u.id = #{id}")
List<Map<String, Object>> getUserWatchlists(Long id);

// 方案 2：XML 映射文件
// resources/mapper/UserMapper.xml
```

## 九、MyBatis-Plus vs 原生 MyBatis

| | 原生 MyBatis | MyBatis-Plus |
|---|---|---|
| CRUD | 要写 XML + SQL | 继承 BaseMapper 自动有 |
| 条件查询 | 写 `<where>` + `<if>` | LambdaQueryWrapper 链式调用 |
| 分页 | 自己写 LIMIT | 内置分页插件 |
| 代码量 | 多（XML 占一半） | 少（Java 代码搞定） |
| 多表联查 | 支持 | 不支持（要写原生 SQL） |
| 学习成本 | 中 | 低 |

## 十、30 秒面试话术

> "项目用 MyBatis-Plus 做 ORM。实体类用 @TableName 映射表名，Mapper 继承 BaseMapper 自动获得 CRUD 能力。条件查询用 LambdaQueryWrapper 链式构造，编译期就能检查字段名。简单查询零 SQL，复杂查询用 @Select 注解写原生 SQL。好处是代码量少一半，简单查询不用写 XML，而且分页、自动填充、逻辑删除都有内置插件。"

## 十一、课后练习

**目标**：给用户表加一个"按手机号查询"功能。

**步骤**：
1. 在 `UserMapper` 里加方法：`UserDO findByPhone(String phone)`
2. 在 `UserService` 里调用
3. 在 `Controller` 里加一个 GET 接口
4. 测试：`GET /api/v1/users/phone/13800000000`

**验收标准**：传入手机号，返回对应的用户信息。

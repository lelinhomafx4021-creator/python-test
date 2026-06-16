# 24_SQLModel数据库操作详解：Python的MyBatis-Plus

> **面向对象**: 有Java背景、正在学习Python的开发者
> **核心目标**: 掌握SQLModel的核心API，能快速上手项目中的数据库操作
> **学习价值**: SQLModel是FastAPI官方推荐的ORM，类似Java的MyBatis-Plus

---

## 📖 第一部分：什么是SQLModel？

### 1.1 一句话定义

**SQLModel = SQLAlchemy + Pydantic**

- SQLAlchemy：Python最强大的ORM（类似Java的Hibernate）
- Pydantic：数据验证（类似Java的Bean Validation）
- SQLModel：两者结合，既做ORM又做数据验证

### 1.2 Java对照

```
┌─────────────────────────────────────────────────────────────┐
│              Java生态 vs Python生态 对照                      │
├─────────────────────────────────────────────────────────────┤
│  MyBatis-Plus         →  SQLModel                            │
│  @TableName           →  class User(SQLModel, table=True)    │
│  @TableId             →  Field(primary_key=True)             │
│  @TableField          →  Field()                             │
│  BaseMapper           →  Session                             │
│  IService             →  自定义Service层                      │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 为什么选择SQLModel？

1. **FastAPI官方推荐**：与FastAPI无缝集成
2. **类型安全**：Pydantic提供运行时类型检查
3. **代码复用**：同一个模型既做数据库映射又做API请求/响应
4. **简单易用**：比原生SQLAlchemy更简洁

---

## 📦 第二部分：模型定义

### 2.1 基础模型

```python
from sqlmodel import SQLModel, Field

class User(SQLModel, table=True):
    """用户表"""
    
    # 主键（自增）
    id: int = Field(primary_key=True)
    
    # 普通字段
    name: str = Field(max_length=50)
    age: int = Field(ge=0, le=150)  # 0 <= age <= 150
    email: str = Field(max_length=100, unique=True)
    
    # 可选字段
    phone: str | None = Field(default=None, max_length=20)
    
    # 有默认值的字段
    is_active: bool = Field(default=True)
    created_at: datetime = Field(default_factory=datetime.now)
```

### 2.2 Java对照

```java
// Java: MyBatis-Plus
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    @TableField("name")
    private String name;
    
    private Integer age;
    
    private String email;
    
    private String phone;
    
    private Boolean isActive;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

### 2.3 字段约束详解

```python
from sqlmodel import SQLModel, Field
from datetime import datetime

class Product(SQLModel, table=True):
    """商品表"""
    
    id: int = Field(primary_key=True)
    
    # 字符串约束
    name: str = Field(
        max_length=100,      # 最大长度
        min_length=1,        # 最小长度
        index=True,          # 创建索引
        unique=True,         # 唯一约束
        description="商品名称"
    )
    
    # 数值约束
    price: float = Field(
        gt=0,                # 大于0
        le=99999.99,         # 小于等于99999.99
        description="商品价格"
    )
    
    # 可选字段
    description: str | None = Field(
        default=None,        # 默认值None
        max_length=1000,
        description="商品描述"
    )
    
    # 外键（手动管理关系）
    category_id: int = Field(
        foreign_key="category.id",  # 外键
        description="分类ID"
    )
    
    # 时间字段
    created_at: datetime = Field(
        default_factory=datetime.now,  # 创建时自动填充
        description="创建时间"
    )
    
    updated_at: datetime = Field(
        default_factory=datetime.now,
        sa_column_kwargs={"onupdate": datetime.now},  # 更新时自动填充
        description="更新时间"
    )
```

### 2.4 表名规则

```python
# 默认表名：类名小写
class User(SQLModel, table=True):
    pass
# 表名：user

class OrderItem(SQLModel, table=True):
    pass
# 表名：orderitem

# 自定义表名
class User(SQLModel, table=True):
    __tablename__ = "users"  # 自定义表名
    id: int = Field(primary_key=True)
```

---

## 🔧 第三部分：数据库连接

### 3.1 创建引擎

```python
from sqlmodel import create_engine, Session, SQLModel

# SQLite（开发测试）
engine = create_engine("sqlite:///database.db")

# PostgreSQL（生产环境）
engine = create_engine(
    "postgresql://user:password@localhost:5432/dbname",
    echo=True  # 打印SQL语句（调试用）
)

# MySQL
engine = create_engine(
    "mysql+pymysql://user:password@localhost:3306/dbname",
    echo=True
)
```

### 3.2 Java对照

```java
// Java: application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dbname
    username: user
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 3.3 创建表

```python
# 创建所有表
SQLModel.metadata.create_all(engine)

# 只创建指定表
SQLModel.metadata.create_all(engine, tables=[User.__table__])
```

### 3.4 项目中的配置

```python
# aipy2/app/core/db.py
from sqlmodel import create_engine, Session

# 从配置获取数据库URL
DATABASE_URL = settings.DATABASE_URL

# 创建引擎
engine = create_engine(DATABASE_URL, echo=settings.DEBUG)

# 创建Session工厂
def get_session():
    """获取数据库会话（类似Java的SqlSession）"""
    with Session(engine) as session:
        yield session
```

---

## 📝 第四部分：CRUD操作

### 4.1 创建（Create）

```python
from sqlmodel import Session

# 方式1: 创建对象并添加
def create_user(session: Session, name: str, age: int, email: str) -> User:
    """创建用户"""
    user = User(name=name, age=age, email=email)
    session.add(user)        # 添加到会话
    session.commit()         # 提交事务
    session.refresh(user)    # 刷新对象（获取数据库生成的id等）
    return user

# 方式2: 批量创建
def create_users(session: Session, users_data: list[dict]) -> list[User]:
    """批量创建用户"""
    users = [User(**data) for data in users_data]
    session.add_all(users)
    session.commit()
    for user in users:
        session.refresh(user)
    return users
```

**Java对照**：
```java
// Java: MyBatis-Plus
public User createUser(String name, int age, String email) {
    User user = new User();
    user.setName(name);
    user.setAge(age);
    user.setEmail(email);
    userMapper.insert(user);
    return user;
}

// 批量
public List<User> createUsers(List<User> users) {
    userMapper.batchInsert(users);
    return users;
}
```

### 4.2 查询（Read）

```python
from sqlmodel import Session, select

# 方式1: 根据ID查询
def get_user_by_id(session: Session, user_id: int) -> User | None:
    """根据ID查询用户"""
    return session.get(User, user_id)

# 方式2: 条件查询（单条）
def get_user_by_email(session: Session, email: str) -> User | None:
    """根据邮箱查询用户"""
    statement = select(User).where(User.email == email)
    return session.exec(statement).first()

# 方式3: 条件查询（多条）
def get_active_users(session: Session) -> list[User]:
    """查询所有活跃用户"""
    statement = select(User).where(User.is_active == True)
    return list(session.exec(statement).all())

# 方式4: 复杂查询
def get_users_by_age_range(session: Session, min_age: int, max_age: int) -> list[User]:
    """按年龄范围查询"""
    statement = (
        select(User)
        .where(User.age >= min_age)
        .where(User.age <= max_age)
        .order_by(User.age)
        .limit(100)
    )
    return list(session.exec(statement).all())

# 方式5: 分页查询
def get_users_page(session: Session, page: int, size: int) -> list[User]:
    """分页查询"""
    statement = (
        select(User)
        .offset((page - 1) * size)
        .limit(size)
    )
    return list(session.exec(statement).all())
```

**Java对照**：
```java
// Java: MyBatis-Plus
public User getUserById(Integer id) {
    return userMapper.selectById(id);
}

public User getUserByEmail(String email) {
    return userMapper.selectOne(
        new QueryWrapper<User>().eq("email", email)
    );
}

public List<User> getActiveUsers() {
    return userMapper.selectList(
        new QueryWrapper<User>().eq("is_active", true)
    );
}

// Lambda查询（推荐，类型安全）
public List<User> getActiveUsers() {
    return userMapper.selectList(
        new LambdaQueryWrapper<User>().eq(User::getIsActive, true)
    );
}
```

### 4.3 更新（Update）

```python
# 方式1: 查询后修改
def update_user_name(session: Session, user_id: int, new_name: str) -> User | None:
    """更新用户名"""
    user = session.get(User, user_id)
    if not user:
        return None
    
    user.name = new_name
    session.add(user)
    session.commit()
    session.refresh(user)
    return user

# 方式2: 批量更新
def deactivate_users(session: Session, user_ids: list[int]) -> int:
    """批量停用用户"""
    statement = select(User).where(User.id.in_(user_ids))
    users = session.exec(statement).all()
    
    for user in users:
        user.is_active = False
        session.add(user)
    
    session.commit()
    return len(users)
```

**Java对照**：
```java
// Java: MyBatis-Plus
public User updateUserName(Integer id, String newName) {
    User user = userMapper.selectById(id);
    if (user == null) return null;
    
    user.setName(newName);
    userMapper.updateById(user);
    return user;
}

// 直接更新
public int deactivateUsers(List<Integer> ids) {
    return userMapper.update(
        null,
        new LambdaUpdateWrapper<User>()
            .in(User::getId, ids)
            .set(User::getIsActive, false)
    );
}
```

### 4.4 删除（Delete）

```python
# 方式1: 根据ID删除
def delete_user(session: Session, user_id: int) -> bool:
    """删除用户"""
    user = session.get(User, user_id)
    if not user:
        return False
    
    session.delete(user)
    session.commit()
    return True

# 方式2: 条件删除
def delete_inactive_users(session: Session) -> int:
    """删除所有停用用户"""
    statement = select(User).where(User.is_active == False)
    users = session.exec(statement).all()
    
    for user in users:
        session.delete(user)
    
    session.commit()
    return len(users)
```

**Java对照**：
```java
// Java: MyBatis-Plus
public boolean deleteUser(Integer id) {
    return userMapper.deleteById(id) > 0;
}

public int deleteInactiveUsers() {
    return userMapper.delete(
        new LambdaQueryWrapper<User>().eq(User::getIsActive, false)
    );
}
```

### 4.5 计数（Count）

```python
from sqlmodel import func

def count_users(session: Session) -> int:
    """统计用户总数"""
    statement = select(func.count()).select_from(User)
    return session.exec(statement).one()

def count_active_users(session: Session) -> int:
    """统计活跃用户数"""
    statement = select(func.count()).where(User.is_active == True).select_from(User)
    return session.exec(statement).one()
```

---

## 🔄 第五部分：事务管理

### 5.1 自动事务（推荐）

```python
from sqlmodel import Session

def transfer_money(session: Session, from_id: int, to_id: int, amount: float):
    """转账（事务示例）"""
    try:
        from_user = session.get(User, from_id)
        to_user = session.get(User, to_id)
        
        if from_user.balance < amount:
            raise ValueError("余额不足")
        
        from_user.balance -= amount
        to_user.balance += amount
        
        session.add(from_user)
        session.add(to_user)
        session.commit()  # 提交事务
        
    except Exception as e:
        session.rollback()  # 回滚事务
        raise e
```

### 5.2 手动事务

```python
from sqlmodel import Session

def complex_operation(session: Session):
    """复杂操作（手动事务）"""
    # 开始事务（Session默认开启事务）
    try:
        # 操作1
        user = User(name="Alice", age=25)
        session.add(user)
        
        # 操作2
        order = Order(user_id=user.id, amount=100)
        session.add(order)
        
        # 提交
        session.commit()
        
    except Exception:
        session.rollback()
        raise
```

### 5.3 Java对照

```java
// Java: Spring事务
@Transactional
public void transferMoney(Integer fromId, Integer toId, BigDecimal amount) {
    User fromUser = userMapper.selectById(fromId);
    User toUser = userMapper.selectById(toId);
    
    if (fromUser.getBalance().compareTo(amount) < 0) {
        throw new RuntimeException("余额不足");
    }
    
    fromUser.setBalance(fromUser.getBalance().subtract(amount));
    toUser.setBalance(toUser.getBalance().add(amount));
    
    userMapper.updateById(fromUser);
    userMapper.updateById(toUser);
}
```

---

## 🏗️ 第六部分：与FastAPI集成

### 6.1 依赖注入

```python
from fastapi import Depends
from sqlmodel import Session

def get_session():
    """获取数据库会话（依赖注入）"""
    with Session(engine) as session:
        yield session

@router.post("/users")
def create_user(
    user_data: UserCreate,
    session: Session = Depends(get_session)  # 注入Session
):
    user = User(**user_data.dict())
    session.add(user)
    session.commit()
    session.refresh(user)
    return user
```

### 6.2 模型复用

```python
from sqlmodel import SQLModel, Field
from pydantic import EmailStr

# 数据库模型
class User(SQLModel, table=True):
    id: int = Field(primary_key=True)
    name: str
    email: str
    hashed_password: str  # 数据库有密码哈希

# API请求模型（继承SQLModel，不需要table=True）
class UserCreate(SQLModel):
    name: str
    email: EmailStr
    password: str  # API接收明文密码

# API响应模型
class UserResponse(SQLModel):
    id: int
    name: str
    email: str  # 响应不包含密码

@router.post("/users", response_model=UserResponse)
def create_user(user_data: UserCreate, session: Session = Depends(get_session)):
    user = User(
        name=user_data.name,
        email=user_data.email,
        hashed_password=hash_password(user_data.password)
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    return user  # FastAPI自动过滤掉hashed_password
```

### 6.3 Java对照

```java
// Java: DTO分离
public class UserCreateDTO {
    private String name;
    private String email;
    private String password;
}

public class UserResponseDTO {
    private Integer id;
    private String name;
    private String email;
}

@PostMapping("/users")
public UserResponseDTO createUser(@RequestBody UserCreateDTO dto) {
    User user = new User();
    user.setName(dto.getName());
    user.setEmail(dto.getEmail());
    user.setHashedPassword(hashPassword(dto.getPassword()));
    userMapper.insert(user);
    
    UserResponseDTO response = new UserResponseDTO();
    response.setId(user.getId());
    response.setName(user.getName());
    response.setEmail(user.getEmail());
    return response;
}
```

---

## 🎯 第七部分：面试速记

### Q1: SQLModel是什么？
**A**: SQLModel = SQLAlchemy + Pydantic，是FastAPI官方推荐的ORM。它既做数据库映射又做数据验证，类似Java的MyBatis-Plus。

### Q2: SQLModel和SQLAlchemy的区别？
**A**: SQLModel基于SQLAlchemy，但更简洁。它用Pydantic风格定义模型，自动处理类型验证，代码量更少。

### Q3: 如何处理事务？
**A**: Session默认开启事务，调用`session.commit()`提交，异常时调用`session.rollback()`回滚。推荐用`try-except`包裹。

### Q4: 如何分页查询？
**A`: 使用`offset()`和`limit()`：`select(User).offset((page-1)*size).limit(size)`。

### Q5: 模型怎么复用？
**A`: 定义基础模型（数据库），继承创建请求/响应模型。FastAPI自动过滤敏感字段。

---

## 📚 项目代码索引

| 文件 | 作用 |
|------|------|
| `aipy2/app/core/db.py` | 数据库连接配置 |
| `aipy2/app/models/` | 数据模型定义 |
| `aipy2/app/graph/state.py` | LangGraph状态（可能用到数据库） |

---

## 🔗 相关笔记

- [[16_Python面向对象详解]] — 类和对象
- [[18_Python文件IO与序列化]] — JSON序列化
- [[31_LangChain核心组件]] — LangChain组件

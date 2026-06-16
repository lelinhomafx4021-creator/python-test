# 21 - Python 工程体系

> Spring Boot 生态的 Python 对应物。FastAPI = Spring Boot，pip = Maven，venv = 没有等价物（Java 不需要）。

## 一、依赖管理

### 1.1 对照表

| Java | Python | 说明 |
|------|--------|------|
| `pom.xml` / `build.gradle` | `pyproject.toml` / `requirements.txt` | 声明依赖 |
| `mvn install` | `pip install -e .` | 安装依赖 |
| Maven Central | PyPI (pypi.org) | 包仓库 |
| `mvn dependency:tree` | `pip list` / `pipdeptree` | 查看依赖树 |
| `mvn clean` | `pip cache purge` | 清理缓存 |
| `groupId:artifactId:version` | `package==version` | 坐标格式 |

### 1.2 项目依赖声明

Java 的 `pom.xml`：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.4.0</version>
</dependency>
```

Python 的 `requirements.txt`（或 `pyproject.toml`）：

```txt
fastapi==0.115.0
langchain-openai==0.3.0
pydantic-settings==2.7.0
```

Python 的 `pyproject.toml`（更现代的写法）：

```toml
[project]
name = "aipy2"
version = "1.0.0"
dependencies = [
    "fastapi>=0.115.0",
    "langchain-openai>=0.3.0",
    "pydantic-settings>=2.7.0",
]
```

### 1.3 安装依赖

```bash
# 安装项目本身（开发模式，改代码不用重新安装）
pip install -e .

# 只装 requirements.txt 里的
pip install -r requirements.txt

# 装单个包
pip install httpx

# 查看已装的包
pip list

# 查看某个包的依赖树
pipdeptree -p langchain
```

## 二、虚拟环境 — Java 没有的概念

### 2.1 为什么需要

```
项目 A 需要 fastapi==0.115.0
项目 B 需要 fastapi==0.100.0

Java 的做法：Maven 自动隔离（每个项目有独立的 .m2 依赖）
Python 的做法：虚拟环境（每个项目有独立的 Python 包目录）
```

### 2.2 使用方法

```bash
# 创建虚拟环境（只需一次）
python -m venv .venv

# 激活虚拟环境（每次打开终端都要执行）
# Windows PowerShell:
.venv\Scripts\Activate.ps1
# Linux/Mac:
source .venv/bin/activate

# 激活后，终端前面会出现 (.venv) 标识
# 此时 pip install 的包只装到 .venv 里，不影响全局

# 退出虚拟环境
deactivate
```

### 2.3 项目结构

```
aipy2/
├── .venv/                  # 虚拟环境（不提交到 git）
│   ├── Lib/site-packages/  # 所有依赖装在这里
│   └── Scripts/python.exe  # 项目专用的 Python 解释器
├── pyproject.toml          # 依赖声明
├── requirements.txt        # 锁定版本
└── main.py                 # 入口
```

**速记**：`.venv` = 项目专属的 Python 环境，类似 Node.js 的 `node_modules`。

## 三、FastAPI — Python 的 Spring Boot

### 3.1 启动类对比

**Spring Boot**：

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins("*");
            }
        };
    }
}
```

**FastAPI**（`aipy2/main.py`）：

```python
app = FastAPI(
    title="AI-Investor-Core",
    version="1.0.0-PRO",
    lifespan=lifespan,                  # 生命周期钩子
)

app.add_middleware(                      # CORS 配置
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router)         # 注册路由模块
app.include_router(kline_router)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

### 3.2 路由对比

| Spring Boot | FastAPI | 说明 |
|------------|---------|------|
| `@GetMapping("/users")` | `@router.get("/users")` | GET 请求 |
| `@PostMapping("/users")` | `@router.post("/users")` | POST 请求 |
| `@RequestParam` | `Query()` | 查询参数 |
| `@RequestBody` | `Body()` 或 Pydantic Model | 请求体 |
| `@PathVariable` | `Path()` | 路径参数 |
| `@RequestMapping` | `APIRouter(prefix=...)` | 路由前缀 |

```python
# 来自 aipy2/app/api/v1/chat.py
router = APIRouter(prefix="/ai/v1", tags=["AI能力层-v1"])

@router.post("/chat", response_model=ChatResponse)
async def post_chat(req: ChatRequest):    # req 自动从请求体解析
    ...
```

### 3.3 请求参数

```python
from fastapi import Query, Body, Path

# 查询参数 — 等价于 @RequestParam
@app.get("/search")
async def search(
    keyword: str = Query(..., description="搜索关键字"),   # 必填
    page: int = Query(1, ge=1),                           # 可选，默认1，最小1
):
    ...

# 请求体 — 等价于 @RequestBody
@app.post("/chat")
async def chat(req: ChatRequest):    # Pydantic model 自动解析 + 校验
    ...

# 路径参数 — 等价于 @PathVariable
@app.get("/users/{user_id}")
async def get_user(user_id: int = Path(...)):
    ...
```

### 3.4 依赖注入

```python
# FastAPI 的 Depends = Spring 的 @Autowired
from fastapi import Depends

def get_db():
    db = SessionLocal()
    try:
        yield db                # yield = 上下文管理器，用完自动关
    finally:
        db.close()

@app.get("/users")
async def list_users(db = Depends(get_db)):
    # db 自动注入，请求结束后自动关闭
    ...
```

Spring 等价：

```java
@Autowired
private UserRepository userRepo;    // Spring 自动注入

@GetMapping("/users")
public List<User> listUsers() {
    return userRepo.findAll();
}
```

### 3.5 生命周期

```python
# 来自 aipy2/main.py
@asynccontextmanager
async def lifespan(app: FastAPI):
    # @PostConstruct：启动时初始化
    await llm_core.init_llm_components()
    yield
    # @PreDestroy：关闭时清理
    await llm_core.shutdown_llm_components()
```

## 四、ORM：SQLModel / SQLAlchemy

### 4.1 实体类对比

**JPA/MyBatis-Plus**：

```java
@Entity
@Table(name = "paper_order")
public class PaperOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String symbol;
}
```

**SQLModel**：

```python
from sqlmodel import SQLModel, Field

class PaperOrder(SQLModel, table=True):
    __tablename__ = "paper_order"

    id: int | None = Field(primary_key=True, default=None)
    account_id: int = Field(nullable=False)
    symbol: str = Field(nullable=False)
```

### 4.2 对照表

| JPA / MyBatis-Plus | SQLModel / SQLAlchemy | 说明 |
|---------------------|----------------------|------|
| `@Entity` | `SQLModel, table=True` | 实体类 |
| `@Table(name="...")` | `__tablename__` | 表名 |
| `@Id` + `@GeneratedValue` | `Field(primary_key=True)` | 主键 |
| `@Column` | `Field(...)` | 字段 |
| `JpaRepository.save()` | `session.add()` + `session.commit()` | 保存 |
| `JpaRepository.findById()` | `session.get()` | 按 ID 查 |
| `findAll()` + Specification | `session.exec(select(...))` | 条件查询 |
| Flyway | Alembic | 数据库迁移 |

### 4.3 查询示例

```java
// Java JPA
User user = userRepo.findById(1L).orElse(null);
List<User> users = userRepo.findByAgeGreaterThan(18);
```

```python
# Python SQLModel
user = session.get(User, 1)
statement = select(User).where(User.age > 18)
users = session.exec(statement).all()
```

## 五、配置管理

### 5.1 对照表

| Spring Boot | Python (pydantic-settings) | 说明 |
|-------------|---------------------------|------|
| `application.yml` | `.env` 文件 | 配置源 |
| `@ConfigurationProperties` | `BaseSettings` 子类 | 绑定配置 |
| `@Value("${key}")` | `settings.KEY` | 读取单个值 |
| `spring.profiles.active` | `APP_ENV` | 环境切换 |

### 5.2 项目配置

**Spring Boot** 的 `application.yml`：

```yaml
app:
  project-name: AI-Investor-Core
  app-env: dev
  database-url: jdbc:mysql://localhost:3306/aiinvestor
```

**Python** 的 `.env`：

```env
PROJECT_NAME=AI-Investor-Core
APP_ENV=dev
DATABASE_URL=postgresql://user:pass@localhost:5432/aiinvestor
DEEPSEEK_API=sk-xxx
```

**Python** 的 `config.py`（`aipy2/app/core/config.py`）：

```python
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "AI-Investor-Core"
    APP_ENV: str = "dev"
    DATABASE_URL: str                          # 必填
    DEEPSEEK_API: str                          # 必填

    model_config = SettingsConfigDict(
        env_file=".env",                       # 从 .env 文件读取
        case_sensitive=False,                  # 大小写不敏感
    )

    @property
    def is_dev(self) -> bool:
        return self.APP_ENV == "dev"

settings = Settings()  # 单例，import 时创建
```

使用：`from app.core.config import settings`，然后 `settings.DATABASE_URL`。

## 六、测试

### 6.1 对照表

| JUnit 5 | pytest | 说明 |
|---------|--------|------|
| `@Test` | `def test_xxx():` | 测试方法（函数名以 `test_` 开头） |
| `@BeforeEach` | `setup_method` 或 fixture | 前置 |
| `@AfterEach` | `teardown_method` 或 fixture | 后置 |
| `assertEquals(a, b)` | `assert a == b` | 断言（原生语法） |
| `assertThrows` | `with pytest.raises(Ex):` | 异常断言 |
| `@Mock` / Mockito | `unittest.mock` / `pytest-mock` | Mock |
| `mvn test` | `pytest` | 运行测试 |

### 6.2 测试示例

```java
// Java JUnit 5
@Test
void testBuildMarketCode() {
    assertEquals("sh600519", buildMarketCode("600519"));
    assertEquals("sz000001", buildMarketCode("000001"));
    assertThrows(IllegalArgumentException.class, () -> buildMarketCode("123"));
}
```

```python
# Python pytest
def test_build_market_code():
    assert build_market_code("600519") == "sh600519"
    assert build_market_code("000001") == "sz000001"

def test_build_market_code_invalid():
    with pytest.raises(ValueError):
        build_market_code("123")
```

### 6.3 pytest fixture

```python
import pytest

@pytest.fixture
def db_session():
    """等价于 JUnit @BeforeEach"""
    session = create_test_session()
    yield session
    session.rollback()     # 测试完回滚
    session.close()

def test_create_user(db_session):
    user = User(name="test")
    db_session.add(user)
    db_session.commit()
    assert user.id is not None
```

## 七、常用工具库速查

| Python 库 | Java 等价 | 用途 |
|-----------|-----------|------|
| `fastapi` | Spring Boot | Web 框架 |
| `httpx` | Spring WebClient | 异步 HTTP 客户端 |
| `pydantic` | Bean Validation | 数据验证 |
| `loguru` | SLF4J + Logback | 日志 |
| `sqlmodel` | JPA / MyBatis-Plus | ORM |
| `alembic` | Flyway | 数据库迁移 |
| `pytest` | JUnit 5 | 测试框架 |
| `rich` | — | 终端美化输出 |
| `pathlib` | `java.nio.file` | 文件路径操作 |
| `requests` | RestTemplate | 同步 HTTP 客户端 |
| `uvicorn` | Tomcat | ASGI 服务器 |
| `python-dotenv` | `.properties` 加载 | 环境变量 |

## 八、项目启动流程对比

### Spring Boot 启动

```
mvn spring-boot:run
  → 加载 application.yml
  → 创建 Spring 容器
  → 扫描 @Component / @Service / @Repository
  → 执行 @PostConstruct
  → 启动 Tomcat (8080)
```

### FastAPI 启动

```bash
python main.py
  → 加载 .env（Settings 类）
  → 创建 FastAPI app
  → 注册路由（include_router）
  → 注册中间件（add_middleware）
  → 执行 lifespan 的 yield 之前部分
  → 启动 uvicorn (8000)
```

项目启动（`aipy2/main.py`）：

```python
# 1. Windows 兼容
if sys.platform.startswith("win"):
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

# 2. 创建 app
app = FastAPI(lifespan=lifespan)

# 3. 注册中间件
app.add_middleware(CORSMiddleware, ...)

# 4. 注册路由
app.include_router(chat_router)
app.include_router(kline_router)

# 5. 启动
uvicorn.run(app, host="0.0.0.0", port=8000)
```

---

## 30 秒电梯演讲

Python 工程体系和 Java 生态一一对应：

1. **FastAPI** = Spring Boot（路由、依赖注入、中间件）
2. **pip + venv** = Maven（依赖管理 + 环境隔离）
3. **SQLModel** = JPA / MyBatis-Plus（ORM）
4. **Alembic** = Flyway（数据库迁移）
5. **pydantic** = Bean Validation（数据校验）
6. **pytest** = JUnit（测试）
7. **`.env` + pydantic-settings** = `application.yml`（配置管理）

## 面试速记

| 问题 | 答案 |
|------|------|
| FastAPI 和 Flask 的区别？ | FastAPI 原生支持 async、自动文档、类型校验 |
| Python 虚拟环境是什么？ | 项目专属的包目录，避免全局依赖冲突 |
| pydantic 的作用？ | 数据验证 + 序列化，等价于 Bean Validation |
| Alembic 是什么？ | SQLAlchemy 的数据库迁移工具，等价于 Flyway |
| uvicorn 是什么？ | ASGI 服务器，等价于 Tomcat |
| Python 的依赖管理用什么？ | pip + requirements.txt 或 pyproject.toml |
| FastAPI 的 Depends 是什么？ | 依赖注入，等价于 Spring 的 @Autowired |

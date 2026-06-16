# 20 - Python 中级特性

> 这是 Java 和 Python 差异最大的部分。装饰器 = AOP，上下文管理器 = try-with-resources，async/await = CompletableFuture。每个都要重点讲。

## 一、装饰器 — Python 的 AOP

### 1.1 对比理解

```
Java AOP：                          Python 装饰器：
┌──────────────────────┐           ┌──────────────────────┐
│  框架级，需要容器支持    │           │  语言级，函数套函数     │
│  @Aspect + @Around    │           │  @decorator           │
│  编译时/运行时织入      │           │  import 时就装饰       │
│  需要 Spring 容器      │           │  纯 Python 就能跑      │
└──────────────────────┘           └──────────────────────┘
```

### 1.2 项目中的装饰器对照表

| Python 装饰器 | Java 等价 | 来源文件 |
|--------------|-----------|---------|
| `@app.middleware("http")` | `HandlerInterceptor` | `main.py` |
| `@router.post("/chat")` | `@PostMapping("/chat")` | `api/v1/chat.py` |
| `@tool` | 无直接等价（LangChain 专属） | `tools/stockdata_tool.py` |
| `@property` | getter 方法 | `core/config.py` |
| `@asynccontextmanager` | 无直接等价 | `main.py` |

### 1.3 装饰器的本质

装饰器就是一个**接收函数、返回函数**的函数：

```python
# 最简单的装饰器
def my_decorator(func):
    def wrapper(*args, **kwargs):
        print("函数执行前")
        result = func(*args, **kwargs)     # 调用原函数
        print("函数执行后")
        return result
    return wrapper

# 使用
@my_decorator
def say_hello():
    print("hello")

say_hello()
# 输出：
# 函数执行前
# hello
# 函数执行后
```

等价于 `say_hello = my_decorator(say_hello)`。

### 1.4 实战：手写计时器装饰器

```python
import time

def timer(func):
    """计时装饰器 — 等价于 Spring AOP 的 @Around + StopWatch"""
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = func(*args, **kwargs)
        elapsed = (time.perf_counter() - start) * 1000
        print(f"{func.__name__} 耗时: {elapsed:.2f}ms")
        return result
    return wrapper

@timer
def slow_function():
    time.sleep(1)

slow_function()  # 输出: slow_function 耗时: 1000.xx ms
```

### 1.5 项目中的装饰器实例

**`@router.post`** — 等价于 Spring 的 `@PostMapping`：

```python
# 来自 aipy2/app/api/v1/chat.py
router = APIRouter(prefix="/ai/v1", tags=["AI能力层-v1"])

@router.post("/chat", response_model=ChatResponse)
async def post_chat(req: ChatRequest):
    ...
```

Java 等价：

```java
@RestController
@RequestMapping("/ai/v1")
public class ChatController {

    @PostMapping("/chat")
    public ChatResponse postChat(@RequestBody ChatRequest req) {
        ...
    }
}
```

**`@app.middleware("http")`** — 等价于 Spring 的 `HandlerInterceptor`：

```python
# 来自 aipy2/main.py
@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    trace_id = request.headers.get("X-Trace-Id", str(uuid.uuid4()))
    start_time = time.perf_counter()
    response = await call_next(request)       # 继续处理请求
    process_time = int((time.perf_counter() - start_time) * 1000)
    response.headers["X-Trace-Id"] = trace_id
    return response
```

**`@tool`** — LangChain 专属，把普通函数变成 AI 可调用的工具：

```python
# 来自 aipy2/app/tools/stockdata_tool.py
from langchain_core.tools import tool

@tool
def get_stock_quote_core(symbol: str) -> str:
    """获取单只 A 股实时行情"""
    ...
```

Java 没有直接等价，最接近的是定义一个接口方法 + 注解。

**`@property`** — 等价于 Java 的 getter：

```python
# 来自 aipy2/app/core/config.py
class Settings(BaseSettings):
    APP_ENV: str = "dev"

    @property
    def is_dev(self) -> bool:
        return self.APP_ENV == "dev"

# 使用
settings.is_dev    # 像属性一样访问，不用加 ()
```

Java 等价：

```java
public boolean isDev() {
    return "dev".equals(this.appEnv);
}
```

## 二、上下文管理器 — Python 的 try-with-resources

### 2.1 对比理解

```java
// Java：try-with-resources，离开块自动关闭
try (var conn = dataSource.getConnection();
     var stmt = conn.prepareStatement(sql)) {
    stmt.executeQuery();
}  // 自动关闭 conn 和 stmt
```

```python
# Python：with 语句，离开块自动清理
with Session(engine) as session:
    session.exec(select(User))
# 自动关闭 session
```

### 2.2 项目中的例子

**FastAPI 的 lifespan** — 等价于 `@PostConstruct` + `@PreDestroy`：

```python
# 来自 aipy2/main.py
@asynccontextmanager
async def lifespan(app: FastAPI):
    # @PostConstruct 阶段：启动时执行
    await llm_core.init_llm_components()
    yield                           # 应用运行中
    # @PreDestroy 阶段：关闭时执行
    await llm_core.shutdown_llm_components()
```

### 2.3 手写上下文管理器

```python
from contextlib import contextmanager

@contextmanager
def database_session():
    """等价于 Java 的 try-with-resources Connection"""
    session = create_session()
    try:
        yield session               # 把 session 交给 with 块使用
    except Exception:
        session.rollback()          # 出错回滚
        raise
    finally:
        session.close()             # 无论如何都关闭

# 使用
with database_session() as session:
    session.execute(...)
```

## 三、模块与包 — Python 的包管理

### 3.1 import 对比

```java
// Java
import com.aiinvestor.gateway.core.config.Settings;
import static com.aiinvestor.gateway.utils.Common.*;
```

```python
# Python
from app.core.config import settings       # 导入具体对象
from app.core.config import Settings        # 导入类
import app.core.llm as llm_core            # 导入模块并起别名
from app.tools.common import *              # 导入所有公开成员
```

### 3.2 `__init__.py` 和 `__all__`

```
app/
├── __init__.py          # 包的标识（可以为空）
├── tools/
│   ├── __init__.py      # 可以定义 __all__ 控制导出
│   ├── common.py
│   └── stockdata_tool.py
```

`__init__.py` = Java 的 `package-info.java`，标记目录是一个 Python 包。

`__all__` = 控制 `from xxx import *` 导出哪些名字：

```python
# app/tools/__init__.py
__all__ = ["common", "stockdata_tool"]  # 只导出这两个
```

### 3.3 模块级代码（重要！）

**Python 在 import 时就执行代码**，Java 的 `static {}` 块类似但不完全一样：

```python
# 来自 aipy2/app/core/config.py
class Settings(BaseSettings):
    ...

settings = Settings()   # 这行在 import 时就执行！创建单例
```

当其他文件写 `from app.core.config import settings` 时，`Settings()` 就已经被调用过了。这就是为什么 `settings` 能当单例用。

Java 等价：

```java
// Spring 的 @Configuration + @Bean
@Configuration
public class Config {
    @Bean
    public Settings settings() {
        return new Settings();  // Spring 容器管理单例
    }
}
```

## 四、异常处理

### 4.1 完整语法

```python
try:
    result = risky_operation()
except ValueError as e:
    # 处理特定异常 — 等价于 catch (ValueException e)
    logger.warning(f"参数错误: {e}")
except (TypeError, KeyError) as e:
    # 同时捕获多种异常 — 等价于 catch (TypeException | KeyException e)
    logger.warning(f"类型或键错误: {e}")
except Exception as e:
    # 捕获所有异常 — 等价于 catch (Exception e)
    logger.error(f"未知错误: {e}")
else:
    # 没有异常时执行（Java 没有这个）
    logger.info("执行成功")
finally:
    # 无论如何都执行
    cleanup()
```

### 4.2 自定义异常

```java
// Java
public class BusinessException extends RuntimeException {
    private int code;
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

```python
# Python
class BusinessException(Exception):
    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code
```

### 4.3 项目中的异常处理

```python
# 来自 aipy2/app/tools/data_fetcher.py
async def fetch_market_data(code: str, client: httpx.AsyncClient) -> dict | None:
    try:
        resp = await client.get(url, timeout=8)
        ...
    except Exception as e:
        logger.warning("行情获取失败 code=%s: %s", code, e)
        return None          # 出错返回 None，不抛异常
```

## 五、类与面向对象

### 5.1 基本对比

```java
// Java
public class User {
    private String name;
    private int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() {
        return "User{name='" + name + "', age=" + age + "}";
    }
}
```

```python
# Python
class User:
    def __init__(self, name: str, age: int):   # 构造器
        self.name = name                        # self = this
        self.age = age

    def __str__(self):                          # toString()
        return f"User(name='{self.name}', age={self.age})"

    # 不需要 getter/setter，直接访问 user.name
```

### 5.2 访问控制

Python 没有 `private/protected/public`，用命名约定：

```python
class Settings:
    PUBLIC_NAME = "hello"      # 公开
    _internal = "不要直接用"    # 约定私有（单下划线）
    __mangled = "强制私有"      # 名称改写（双下划线，很少用）
```

**速记**：单下划线 `_` 开头 = "请不要直接用"，但没有强制约束。

### 5.3 `@property` 代替 getter/setter

```python
class Config:
    def __init__(self, env: str):
        self._env = env

    @property
    def env(self) -> str:           # getter
        return self._env

    @env.setter
    def env(self, value: str):      # setter
        self._env = value.lower()

c = Config("DEV")
c.env           # 调用 getter，返回 "DEV"
c.env = "PROD"  # 调用 setter
```

### 5.4 鸭子类型

Java 需要 `interface` 来定义契约，Python 不需要 — 有同样的方法就能用：

```java
// Java：必须实现接口
public class Duck implements Swimmable, Flyable {
    public void swim() { ... }
    public void fly() { ... }
}
```

```python
# Python：有 swim 和 fly 方法就行，不需要声明
class Duck:
    def swim(self):
        print("游泳")
    def fly(self):
        print("飞")

# 只要有 swim 方法，任何类都能当"鸭子"用
def make_it_swim(thing):
    thing.swim()    # 不检查类型，有 swim 就行
```

### 5.5 项目中的类

```python
# 来自 aipy2/app/graph/investor_graph.py
class MultiGraphInvestorAgent:
    """对外提供统一的 LangGraph 调用入口"""

    def __init__(self):
        self._graph_normal = None      # 实例变量
        self._graph_vip = None

    def _get_graph(self, role: str = "normal"):
        """约定私有方法"""
        if role == "vip":
            if self._graph_vip is None:
                self._graph_vip = build_self_rag_graph(role="vip")
            return self._graph_vip
        else:
            if self._graph_normal is None:
                self._graph_normal = build_self_rag_graph(role="normal")
            return self._graph_normal

    @property
    def graph(self):
        """属性访问，不用加括号"""
        return self._get_graph("normal")

    async def ask_stream_events(self, query, thread_id, role="normal"):
        """异步生成器方法"""
        ...
        yield {"stage": "accepted", "data": {"query": query}}
        ...
        yield {"stage": "final_answer", "data": {...}}
```

## 六、async/await — 异步编程

### 6.1 并发模型对比

```
Java 并发：                          Python 并发：
┌──────────────────────┐           ┌──────────────────────┐
│  多线程（真正的并行）    │           │  单线程事件循环         │
│  线程池 + CompletableFuture │     │  asyncio + async/await │
│  可以利用多核 CPU      │           │  GIL 限制了多线程      │
│  适合 CPU 密集型       │           │  适合 IO 密集型        │
└──────────────────────┘           └──────────────────────┘
```

**GIL（全局解释器锁）**：Python 同一时刻只能有一个线程执行 Python 代码。所以 Python 的多线程不能利用多核 CPU，但 IO 等待时可以切换。

### 6.2 语法对比

```java
// Java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return fetchData();
});
String result = future.get();  // 阻塞等待

// Java 并行
CompletableFuture.allOf(future1, future2, future3).join();
```

```python
# Python
async def fetch_data():
    return await some_io_operation()

# 并行执行多个任务（等价于 CompletableFuture.allOf）
results = await asyncio.gather(
    fetch_market_data(code, client),
    fetch_financial_data(code, client),
    fetch_announcements(code, client),
)
```

### 6.3 常用 API 对照

| Java | Python | 说明 |
|------|--------|------|
| `CompletableFuture.allOf()` | `asyncio.gather()` | 并行等待多个任务 |
| `CompletableFuture.supplyAsync()` | `asyncio.to_thread()` | 放到线程池执行 |
| `@Async` | `asyncio.create_task()` | 创建后台任务 |
| `Thread.sleep(1000)` | `await asyncio.sleep(1)` | 异步等待 |

### 6.4 项目中的 async/await

**并行数据获取**（`aipy2/app/tools/data_fetcher.py`）：

```python
async def fetch_all_data_parallel(query, queries, top_k=3):
    async with httpx.AsyncClient() as client:
        # 5 个任务同时发出，总耗时 = 最慢那个
        results = await asyncio.gather(
            fetch_market_data(symbol, client),      # 行情
            fetch_financial_data(symbol, client),    # 财务
            fetch_announcements(symbol, client),     # 公告
            fetch_news_data(query),                  # 新闻
            fetch_retrieval_data(queries, top_k),    # 检索
            return_exceptions=True,                  # 单个失败不影响其他
        )
```

Java 等价：

```java
var market = CompletableFuture.supplyAsync(() -> fetchMarketData(code));
var financial = CompletableFuture.supplyAsync(() -> fetchFinancialData(code));
var news = CompletableFuture.supplyAsync(() -> fetchNewsData(query));
CompletableFuture.allOf(market, financial, news).join();
```

**同步函数放线程池**：

```python
# collect_hot_news 是同步函数，用 asyncio.to_thread 放到线程池
return await asyncio.to_thread(collect_hot_news, limit=8)
```

Java 等价：`CompletableFuture.supplyAsync(() -> collectHotNews(8))`

## 七、生成器与迭代器

### 7.1 `yield` 关键字

`yield` 让函数"暂停"，下次调用时从暂停处继续：

```python
def count_up():
    yield 1    # 暂停，返回 1
    yield 2    # 暂停，返回 2
    yield 3    # 暂停，返回 3

for n in count_up():
    print(n)   # 输出 1, 2, 3
```

### 7.2 三种用途

**用途一：惰性求值** — 不一次性生成所有数据

```python
# 生成 100 万个数，但不占用 100 万个内存
def big_range():
    for i in range(1_000_000):
        yield i
```

**用途二：SSE 流式输出** — 项目核心

```python
# 来自 aipy2/app/api/v1/chat.py
async def event_gen():
    async for evt in investor_service.run_investor_flow(...):
        payload = json.dumps(evt, ensure_ascii=False)
        yield f"event: message\ndata: {payload}\n\n"   # 每次 yield 一帧

return StreamingResponse(event_gen(), media_type="text/event-stream")
```

**用途三：DI 会话管理** — 等价于 try-with-resources

```python
# 来自 aipy2/main.py
@asynccontextmanager
async def lifespan(app: FastAPI):
    await init()     # 启动
    yield            # 运行中
    await shutdown() # 关闭
```

### 7.3 异步生成器

普通生成器用 `for ... in`，异步生成器用 `async for ... in`：

```python
# 来自 aipy2/app/graph/investor_graph.py
async def ask_stream_events(self, query, thread_id, role="normal"):
    yield {"stage": "accepted", "data": {"query": query}}

    async for mode, chunk in graph.astream(input_data, config=config):
        # 处理每个 chunk
        yield {"stage": node_name, "data": {"step": updates["step"]}}

    yield {"stage": "final_answer", "data": {...}}
    yield {"stage": "done", "data": {"status": "success"}}
```

调用方：

```python
# 来自 aipy2/app/api/v1/chat.py
async for evt in investor_service.run_investor_flow(...):
    if evt["stage"] == "final_answer":
        answer_text = evt["data"]["answer"]
```

---

## 30 秒电梯演讲

Python 中级特性和 Java 差异最大：

1. **装饰器** = AOP，函数套函数，语言级实现
2. **上下文管理器** = try-with-resources，`with` 语句自动清理
3. **async/await** = CompletableFuture，单线程事件循环
4. **生成器** = `yield` 暂停/恢复，SSE 流式输出的核心
5. **鸭子类型** = 不需要 interface，有方法就能用
6. **模块级代码** = import 时就执行，天然单例

## 面试速记

| 问题 | 答案 |
|------|------|
| Python 装饰器是什么？ | 接收函数返回函数的高阶函数，等价于 AOP |
| `with` 语句的本质？ | 上下文管理器，`__enter__` 进入，`__exit__` 退出 |
| Python 的 GIL 是什么？ | 全局解释器锁，限制同一时刻只有一个线程执行 Python 代码 |
| `asyncio.gather` 等价于 Java 的什么？ | `CompletableFuture.allOf()` |
| `yield` 和 `return` 区别？ | `yield` 暂停不结束，`return` 直接结束 |
| Python 的鸭子类型？ | 不需要 interface，有同样方法就能用 |
| `@property` 的作用？ | 把方法伪装成属性访问，等价于 getter |

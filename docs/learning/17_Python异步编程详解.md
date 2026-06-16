# 17_Python异步编程详解：async/await、事件循环的Java对照学习

> **面向对象**: 有Java背景、正在学习Python的开发者
> **核心目标**: 掌握Python异步编程的核心概念和API
> **学习价值**: 异步编程是AI应用的必备技能，FastAPI/LangChain都依赖它

---

## 📖 第一部分：什么是异步编程？

### 1.1 同步 vs 异步

```
同步（阻塞）:
你去餐厅点餐 → 等着厨师做好 → 拿到餐 → 吃

异步（非阻塞）:
你去餐厅点餐 → 拿到取餐号 → 做其他事 → 叫号取餐 → 吃
```

### 1.2 Java对照

```java
// Java: 同步
String result = httpClient.get(url);  // 阻塞等待
System.out.println(result);

// Java: 异步（CompletableFuture）
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return httpClient.get(url);
});
future.thenAccept(result -> {
    System.out.println(result);
});
// 不阻塞，可以做其他事
```

```python
# Python: 同步
result = requests.get(url)  # 阻塞等待
print(result)

# Python: 异步（async/await）
result = await aiohttp.get(url)  # 非阻塞
print(result)
```

### 1.3 为什么需要异步？

**场景**：AI应用中同时调用多个LLM API

```python
# 同步：串行执行，总时间 = 3秒
result1 = call_llm("问题1")  # 1秒
result2 = call_llm("问题2")  # 1秒
result3 = call_llm("问题3")  # 1秒
# 总时间：3秒

# 异步：并行执行，总时间 = 1秒
results = await asyncio.gather(
    call_llm("问题1"),
    call_llm("问题2"),
    call_llm("问题3")
)
# 总时间：1秒（同时执行）
```

---

## ⚡ 第二部分：async/await基础

### 2.1 定义异步函数

```python
import asyncio

# 定义异步函数（协程）
async def say_hello(name: str) -> str:
    """异步函数"""
    await asyncio.sleep(1)  # 模拟IO操作
    return f"Hello, {name}!"

# 调用异步函数（返回协程对象，不会立即执行）
coroutine = say_hello("Alice")

# 执行协程
result = asyncio.run(say_hello("Alice"))
print(result)  # "Hello, Alice!"
```

### 2.2 Java对照

```java
// Java: CompletableFuture
public CompletableFuture<String> sayHello(String name) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "Hello, " + name + "!";
    });
}

// 使用
CompletableFuture<String> future = sayHello("Alice");
String result = future.get();  // 阻塞获取结果
```

### 2.3 await关键字

```python
async def fetch_data():
    """await: 等待异步操作完成，但不阻塞事件循环"""
    print("开始获取数据...")
    
    # await会暂停当前协程，让事件循环执行其他协程
    data = await some_async_io()
    
    print(f"获取到数据: {data}")
    return data
```

**关键点**：
- `await`只能在`async`函数中使用
- `await`会暂停当前协程，但不阻塞整个程序
- `await`等待的操作完成后，协程会继续执行

---

## 🔄 第三部分：asyncio核心API

### 3.1 asyncio.run() — 运行协程

```python
import asyncio

async def main():
    print("Hello")
    await asyncio.sleep(1)
    print("World")

# 方式1: asyncio.run()（推荐，Python 3.7+）
asyncio.run(main())

# 方式2: 获取事件循环（旧方式）
loop = asyncio.get_event_loop()
loop.run_until_complete(main())
```

### 3.2 asyncio.sleep() — 异步睡眠

```python
async def delayed_hello(name: str, delay: int):
    """延迟输出"""
    await asyncio.sleep(delay)  # 非阻塞睡眠
    print(f"Hello, {name}!")

async def main():
    # 串行执行：总时间 = 3秒
    await delayed_hello("Alice", 1)
    await delayed_hello("Bob", 1)
    await delayed_hello("Charlie", 1)

asyncio.run(main())
```

### 3.3 asyncio.gather() — 并行执行

```python
async def fetch_user(user_id: int):
    """获取用户信息"""
    await asyncio.sleep(1)  # 模拟IO
    return {"id": user_id, "name": f"User{user_id}"}

async def main():
    # 并行执行：总时间 = 1秒（不是3秒）
    results = await asyncio.gather(
        fetch_user(1),
        fetch_user(2),
        fetch_user(3)
    )
    print(results)
    # [{'id': 1, 'name': 'User1'}, {'id': 2, 'name': 'User2'}, {'id': 3, 'name': 'User3'}]

asyncio.run(main())
```

**Java对照**：
```java
// Java: CompletableFuture.allOf()
CompletableFuture<User> f1 = fetchUser(1);
CompletableFuture<User> f2 = fetchUser(2);
CompletableFuture<User> f3 = fetchUser(3);

CompletableFuture.allOf(f1, f2, f3).join();
List<User> results = List.of(f1.get(), f2.get(), f3.get());
```

### 3.4 asyncio.create_task() — 创建任务

```python
async def fetch_data(url: str):
    """获取数据"""
    await asyncio.sleep(1)
    return f"Data from {url}"

async def main():
    # 创建任务（立即开始执行）
    task1 = asyncio.create_task(fetch_data("url1"))
    task2 = asyncio.create_task(fetch_data("url2"))
    
    # 做其他事
    print("Doing other work...")
    
    # 等待任务完成
    result1 = await task1
    result2 = await task2
    
    print(result1, result2)

asyncio.run(main())
```

### 3.5 asyncio.wait() — 等待任务

```python
async def main():
    tasks = [
        asyncio.create_task(fetch_data("url1")),
        asyncio.create_task(fetch_data("url2")),
        asyncio.create_task(fetch_data("url3"))
    ]
    
    # 等待所有任务完成
    done, pending = await asyncio.wait(tasks, return_when=asyncio.ALL_COMPLETED)
    
    for task in done:
        print(task.result())

asyncio.run(main())
```

### 3.6 asyncio.as_completed() — 按完成顺序获取结果

```python
async def main():
    tasks = [
        asyncio.create_task(fetch_data("url1")),
        asyncio.create_task(fetch_data("url2")),
        asyncio.create_task(fetch_data("url3"))
    ]
    
    # 按完成顺序获取结果（不是按提交顺序）
    for coro in asyncio.as_completed(tasks):
        result = await coro
        print(f"Completed: {result}")

asyncio.run(main())
```

---

## 📡 第四部分：异步IO操作

### 4.1 异步HTTP请求（aiohttp）

```python
import aiohttp
import asyncio

async def fetch_url(url: str) -> str:
    """异步HTTP GET"""
    async with aiohttp.ClientSession() as session:
        async with session.get(url) as response:
            return await response.text()

async def main():
    # 并行请求多个URL
    urls = [
        "https://api.example.com/1",
        "https://api.example.com/2",
        "https://api.example.com/3"
    ]
    
    tasks = [fetch_url(url) for url in urls]
    results = await asyncio.gather(*tasks)
    
    for url, result in zip(urls, results):
        print(f"{url}: {len(result)} bytes")

asyncio.run(main())
```

**Java对照**：
```java
// Java: WebClient (Spring WebFlux)
WebClient client = WebClient.create();

Mono<String> result = client.get()
    .uri("https://api.example.com/1")
    .retrieve()
    .bodyToMono(String.class);

result.subscribe(System.out::println);
```

### 4.2 异步文件操作（aiofiles）

```python
import aiofiles
import asyncio

async def read_file(path: str) -> str:
    """异步读取文件"""
    async with aiofiles.open(path, 'r') as f:
        return await f.read()

async def write_file(path: str, content: str):
    """异步写入文件"""
    async with aiofiles.open(path, 'w') as f:
        await f.write(content)

async def main():
    # 异步读取
    content = await read_file("data.txt")
    print(content)
    
    # 异步写入
    await write_file("output.txt", "Hello, async!")

asyncio.run(main())
```

### 4.3 异步数据库操作（asyncpg）

```python
import asyncpg
import asyncio

async def query_database():
    """异步数据库查询"""
    conn = await asyncpg.connect('postgresql://user:pass@localhost/db')
    
    try:
        # 异步查询
        rows = await conn.fetch('SELECT * FROM users WHERE age > $1', 25)
        return rows
    finally:
        await conn.close()

async def main():
    users = await query_database()
    for user in users:
        print(user['name'], user['age'])

asyncio.run(main())
```

---

## 🏗️ 第五部分：异步上下文管理器

### 5.1 async with

```python
class AsyncDatabaseConnection:
    """异步数据库连接"""
    
    def __init__(self, url):
        self.url = url
        self.conn = None
    
    async def __aenter__(self):
        """异步进入"""
        self.conn = await asyncpg.connect(self.url)
        return self.conn
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """异步退出"""
        if self.conn:
            await self.conn.close()

async def main():
    async with AsyncDatabaseConnection('postgresql://...') as conn:
        rows = await conn.fetch('SELECT * FROM users')
        print(rows)
    # 连接自动关闭

asyncio.run(main())
```

### 5.2 异步迭代器

```python
class AsyncRange:
    """异步迭代器"""
    
    def __init__(self, start, end):
        self.start = start
        self.end = end
        self.current = start
    
    def __aiter__(self):
        return self
    
    async def __anext__(self):
        if self.current >= self.end:
            raise StopAsyncIteration
        
        await asyncio.sleep(0.1)  # 模拟异步操作
        self.current += 1
        return self.current - 1

async def main():
    async for i in AsyncRange(0, 5):
        print(i)  # 0, 1, 2, 3, 4

asyncio.run(main())
```

---

## 🔧 第六部分：异步生成器

### 6.1 async yield

```python
async def async_generator():
    """异步生成器"""
    for i in range(5):
        await asyncio.sleep(0.1)
        yield i

async def main():
    # 异步遍历
    async for value in async_generator():
        print(value)
    
    # 收集所有值
    values = [value async for value in async_generator()]
    print(values)  # [0, 1, 2, 3, 4]

asyncio.run(main())
```

### 6.2 实际应用：流式数据处理

```python
async def fetch_stream(url: str):
    """流式获取数据"""
    async with aiohttp.ClientSession() as session:
        async with session.get(url) as response:
            async for chunk in response.content.iter_chunked(1024):
                yield chunk

async def main():
    async for chunk in fetch_stream("https://api.example.com/stream"):
        process(chunk)

asyncio.run(main())
```

---

## 📊 第七部分：同步与异步转换

### 7.1 在异步中调用同步代码

```python
import asyncio

def sync_function():
    """同步函数"""
    import time
    time.sleep(1)  # 阻塞操作
    return "done"

async def main():
    # 方式1: run_in_executor（推荐）
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(None, sync_function)
    print(result)
    
    # 方式2: 使用默认执行器
    result = await asyncio.get_event_loop().run_in_executor(
        None,  # 使用默认线程池
        sync_function
    )
    print(result)

asyncio.run(main())
```

### 7.2 在同步中调用异步代码

```python
import asyncio

async def async_function():
    """异步函数"""
    await asyncio.sleep(1)
    return "done"

# 方式1: asyncio.run()（推荐）
result = asyncio.run(async_function())

# 方式2: 获取事件循环
loop = asyncio.get_event_loop()
result = loop.run_until_complete(async_function())
```

---

## 🎯 第八部分：项目中的应用

### 8.1 FastAPI异步路由

```python
from fastapi import APIRouter

router = APIRouter()

@router.get("/users/{user_id}")
async def get_user(user_id: int):
    """异步路由处理"""
    # 异步数据库查询
    user = await db.fetch_user(user_id)
    
    # 异步HTTP请求
    extra_data = await external_api.get_data(user_id)
    
    return {"user": user, "extra": extra_data}
```

### 8.2 LangChain异步调用

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4")

async def call_llm(question: str) -> str:
    """异步调用LLM"""
    response = await llm.ainvoke(question)
    return response.content

async def main():
    # 并行调用多个LLM
    results = await asyncio.gather(
        call_llm("问题1"),
        call_llm("问题2"),
        call_llm("问题3")
    )
    print(results)

asyncio.run(main())
```

### 8.3 LangGraph异步执行

```python
from langgraph.graph import StateGraph

graph = StateGraph(AgentState)
# ... 添加节点和边 ...

app = graph.compile()

async def run_agent(question: str):
    """异步执行Agent"""
    async for event in app.astream_events(
        {"messages": [("user", question)]},
        version="v2"
    ):
        if event["event"] == "on_chat_model_stream":
            token = event["data"]["chunk"].content
            yield token

# 流式输出
async for token in run_agent("你好"):
    print(token, end="")
```

---

## 🎯 第九部分：面试速记

### Q1: async/await的作用？
**A**: `async`定义异步函数，`await`等待异步操作完成但不阻塞事件循环。让IO密集型任务可以并行执行，提高性能。

### Q2: asyncio.gather和asyncio.wait的区别？
**A**: `gather`按提交顺序返回结果，`wait`返回done和pending两个集合。`gather`更常用，`wait`更灵活（可以设置超时、等待条件）。

### Q3: 什么时候用异步？
**A**: IO密集型任务（网络请求、文件读写、数据库查询）。CPU密集型任务用多进程，不是异步。

### Q4: 如何在异步中调用同步代码？
**A**: 使用`loop.run_in_executor(None, sync_function)`，将同步函数放到线程池中执行。

### Q5: 什么是事件循环？
**A**: 事件循环是异步编程的核心，负责调度和执行协程。当协程await时，事件循环会执行其他协程，实现并发。

---

## 🔗 相关笔记

- [[15_Python函数详解]] — 函数定义和参数
- [[20_Python中级特性]] — Python中级特性
- [[31_LangChain核心组件]] — LangChain异步调用

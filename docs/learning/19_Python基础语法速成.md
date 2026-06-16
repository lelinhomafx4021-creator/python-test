# 19 - Python 基础语法速通

> Java 基础你已经会了，这里只讲"不一样的地方"。30 分钟过一遍，能看懂项目代码就行。

## 一、动态类型 vs 静态类型

Java 必须声明类型，Python 不用。类型在运行时才确定。

```java
// Java：编译时就确定类型
int x = 1;
String name = "hello";
List<String> items = new ArrayList<>();
```

```python
# Python：运行时才确定类型
x = 1            # int
name = "hello"   # str
items = []       # list，不需要 <>
```

Python 3.5+ 支持 **type hints**（类型注解），但只是"建议"，不影响运行：

```python
# 来自 aipy2/app/core/config.py
class Settings(BaseSettings):
    PROJECT_NAME: str = "AI-Investor-Core"   # : str 是类型注解
    APP_ENV: str = "dev"
    DATABASE_URL: str                        # 没有默认值，必须传
    LANGFUSE_ENABLED: bool = True            # bool 类型
```

对比 Java 的 `@ConfigurationProperties`：

```java
// Java 等价写法
@ConfigurationProperties(prefix = "app")
public class Settings {
    private String projectName = "AI-Investor-Core";
    private String appEnv = "dev";
    private String databaseUrl;
    private boolean langfuseEnabled = true;
}
```

**速记**：Python 的 `str` = Java 的 `String`，`int` = `int`，`bool` = `boolean`，`float` = `double`。

## 二、缩进就是大括号

Python 用缩进代替 `{}`，用 `:` 代替 `{`：

```java
// Java
if (x > 0) {
    System.out.println("正数");
    if (x > 100) {
        System.out.println("大数");
    }
}
```

```python
# Python
if x > 0:
    print("正数")
    if x > 100:
        print("大数")
```

**常见坑**：混用 Tab 和空格会报 `TabError`。建议统一用 4 个空格（PEP 8 规范）。

**速记**：看到 `:` 就缩进一级，退出缩进就是退出代码块。

## 三、数据结构对比

| Java | Python | 写法 | 说明 |
|------|--------|------|------|
| `ArrayList` | `list` | `[1, 2, 3]` | 可变有序 |
| `HashMap` | `dict` | `{"k": "v"}` | 键值对 |
| `HashSet` | `set` | `{1, 2, 3}` | 去重无序 |
| `不可变 List` | `tuple` | `(1, 2)` | 不可修改 |
| `String` | `str` | `"hello"` | 不可变，支持切片 |
| `null` | `None` | — | 用 `is None` 判断 |

### 3.1 list 切片（Java 没有）

```python
items = [10, 20, 30, 40, 50]

items[1:3]    # [20, 30]      — 从索引1到3（不含3）
items[:3]     # [10, 20, 30]  — 前3个
items[2:]     # [30, 40, 50]  — 从索引2到末尾
items[::-1]   # [50, 40, 30, 20, 10]  — 反转
```

Java 等价：`list.subList(1, 3)` 或 `Collections.reverse(list)`。

### 3.2 dict 推导式 vs Java Stream

```java
// Java：把 list 转成 map
Map<String, Integer> map = list.stream()
    .collect(Collectors.toMap(s -> s, s -> s.length()));
```

```python
# Python：dict 推导式
map = {s: len(s) for s in items}
```

### 3.3 None 判断

```python
# 正确写法
if x is None:
    print("空")

# 错误写法（能跑但不推荐）
if x == None:
    print("空")
```

`is None` 比 `== None` 更快，因为 `is` 比较的是内存地址，`==` 比较的是值。

## 四、列表推导式（最 Pythonic 的语法）

```java
// Java Stream
List<Integer> result = list.stream()
    .filter(x -> x > 0)
    .map(x -> x * 2)
    .collect(Collectors.toList());
```

```python
# Python 列表推导式：一行搞定
result = [x * 2 for x in data if x > 0]
```

三种推导式：

```python
# 列表推导式
squares = [x**2 for x in range(10)]        # [0, 1, 4, 9, ...]

# 字典推导式
square_map = {x: x**2 for x in range(5)}   # {0: 0, 1: 1, 2: 4, ...}

# 集合推导式
even_set = {x for x in range(10) if x % 2 == 0}  # {0, 2, 4, 6, 8}
```

**项目中的例子**（`aipy2/app/tools/data_fetcher.py`）：

```python
# 把公告列表拼成文本
ann_text = "\n".join([f"- {a['date']}: {a['title']}" for a in ann])
```

## 五、函数定义

### 5.1 基本语法

```java
// Java
public static String buildMarketCode(String symbol) {
    return "sh" + symbol;
}
```

```python
# Python：def 代替 public void，缩进代替大括号
def build_market_code(symbol: str) -> str:
    return f"sh{symbol}"
```

### 5.2 默认参数

```python
# Java 没有默认参数，只能重载
# Python 直接写默认值
def get_llm(temperature: float = 0.2, streaming: bool = False):
    ...

get_llm()              # 用默认值
get_llm(0.5)           # 只改 temperature
get_llm(0.5, True)     # 按位置传
get_llm(streaming=True)  # 按名字传（推荐）
```

项目例子（`aipy2/app/core/llm.py`）：

```python
def get_llm(
    temperature: float = 0.2,
    *,
    streaming: bool = False,           # * 后面的参数必须按名字传
    max_completion_tokens: int | None = None,
) -> ChatOpenAI:
```

### 5.3 可变参数

```java
// Java
public void log(String... args) { }
```

```python
# Python
def log(*args, **kwargs):
    # args   = (值1, 值2, ...)     — 位置参数元组
    # kwargs = {"key": "value"}    — 关键字参数字典
    pass

log("hello", "world", level="INFO")
# args = ("hello", "world")
# kwargs = {"level": "INFO"}
```

### 5.4 lambda

```java
// Java
Function<Integer, Integer> double_it = x -> x * 2;
```

```python
# Python：lambda 更简洁
double_it = lambda x: x * 2
```

### 5.5 返回值

Python 函数可以返回任意类型，不需要重载：

```python
def safe_text(value, fallback=""):
    if value is None:
        return fallback        # 返回 str
    text = str(value).strip()
    return text or fallback    # 返回 str

def extract_stock_code(text: str) -> str | None:
    match = _STOCK_CODE_RE.search(text)
    return match.group(1) if match else None   # 返回 str 或 None
```

## 六、f-string 格式化

```java
// Java
String msg = String.format("股票%s现价%.2f元", name, price);

// Java 17+
String msg = "股票%s现价%.2f元".formatted(name, price);
```

```python
# Python f-string（最常用）
msg = f"股票{name}现价{price}元"

# 带表达式
msg = f"总价: {price * quantity:.2f}元"   # :.2f 表示保留2位小数
```

项目例子（`aipy2/app/tools/data_fetcher.py`）：

```python
market_text = (
    f"股票{market['name']}({market['symbol']})："
    f"现价{market['lastPrice']}元，"
    f"涨跌幅{market['changePercent']}%"
)
```

## 七、for 循环和迭代

```java
// Java：遍历 list
for (int i = 0; i < list.size(); i++) {
    System.out.println(i + ": " + list.get(i));
}

// Java：遍历 map
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

```python
# Python：enumerate 同时拿索引和值
for i, item in enumerate(items):
    print(f"{i}: {item}")

# Python：遍历 dict
for key, value in d.items():
    print(f"{key}: {value}")

# Python：只遍历值
for value in d.values():
    print(value)
```

**项目中的例子**（`aipy2/app/tools/stockdata_tool.py`）：

```python
for line in [item for item in text.split(";") if item.strip()]:
    if '"' not in line:
        continue
    raw_code = line.split("=", 1)[0].replace("v_", "").strip()
    payload = line.split('"', 2)[1]
```

## 八、三元表达式和海象运算符

### 三元表达式

```java
// Java
int max = a > b ? a : b;
String status = score >= 60 ? "及格" : "不及格";
```

```python
# Python
max_val = a if a > b else b
status = "及格" if score >= 60 else "不及格"
```

### 海象运算符（Python 3.8+）

赋值 + 判断一步到位：

```python
# 没有海象运算符
n = len(data)
if n > 10:
    print(f"数据量 {n} 太大")

# 有海象运算符 :=
if (n := len(data)) > 10:
    print(f"数据量 {n} 太大")
```

**项目中的例子**（`aipy2/app/tools/data_fetcher.py`）：

```python
# 嵌套字典安全取值 + 判断
items = data.get("list", []) if isinstance(data, dict) else []
```

## 九、字符串操作速查

```python
s = "Hello, World!"

s.lower()           # "hello, world!"
s.upper()           # "HELLO, WORLD!"
s.strip()           # 去首尾空白
s.split(",")        # ["Hello", " World!"]
s.replace("H", "J") # "Jello, World!"
s.startswith("H")   # True
s.endswith("!")     # True
"hello" in s        # True（子串判断）
",".join(["a","b"]) # "a,b"（拼接）
```

## 十、异常处理速览

```java
// Java
try {
    riskyOperation();
} catch (IOException e) {
    log.error("失败", e);
} finally {
    cleanup();
}
```

```python
# Python
try:
    risky_operation()
except IOError as e:
    logger.error(f"失败: {e}")
finally:
    cleanup()
```

**速记**：`catch` → `except`，`Exception e` → `Exception as e`，其余一样。

---

## 30 秒电梯演讲

Python 基础语法和 Java 80% 相似。核心差异：

1. **动态类型**：不用声明类型，但可以用 type hints
2. **缩进代替大括号**：看到 `:` 就缩进
3. **列表推导式**：`[x*2 for x in data if x > 0]` 一行顶 Java 五行
4. **f-string**：`f"hello {name}"` 比 `String.format` 简洁
5. **`None` 用 `is` 判断**：`if x is None`
6. **`def` 代替 `public void`**：函数定义更简洁

## 面试速记

| 问题 | 答案 |
|------|------|
| Python 和 Java 最大的语法差异？ | 动态类型 + 缩进代替大括号 |
| `is None` 和 `== None` 区别？ | `is` 比较地址（推荐），`==` 比较值 |
| 什么是列表推导式？ | `[表达式 for x in 可迭代 if 条件]` |
| Python 的 `*args` 和 `**kwargs`？ | 位置参数元组 + 关键字参数字典 |
| f-string 是什么？ | `f"hello {name}"`，Python 的字符串模板 |

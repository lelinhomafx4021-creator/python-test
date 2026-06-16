# 15_Python函数详解：参数、装饰器、lambda的Java对照学习

> **面向对象**: 有Java背景、正在学习Python的开发者
> **核心目标**: 掌握Python函数的各种参数类型和高级特性
> **学习价值**: 函数是编程核心，装饰器是Python面试必问

---

## 📖 第一部分：函数定义基础

### 1.1 基础语法

```python
# 定义函数
def greet(name):
    """问候函数（这是docstring，类似Java的JavaDoc）"""
    return f"Hello, {name}!"

# 调用函数
result = greet("Alice")
print(result)  # "Hello, Alice!"
```

### 1.2 Java对照

```java
// Java
/**
 * 问候函数
 */
public String greet(String name) {
    return "Hello, " + name + "!";
}
```

### 1.3 类型注解（Python 3.5+）

```python
# 带类型注解的函数（推荐，提高代码可读性）
def greet(name: str) -> str:
    """问候函数"""
    return f"Hello, {name}!"

# 复杂类型
from typing import List, Dict, Optional

def process_users(users: List[Dict[str, str]], active: bool = True) -> Optional[str]:
    """处理用户列表"""
    if not users:
        return None
    return users[0]["name"]
```

**Java对照**：
```java
// Java: 天然有类型
public String greet(String name) {
    return "Hello, " + name + "!";
}
```

---

## 📦 第二部分：参数类型详解

### 2.1 位置参数（Positional Arguments）

```python
def add(a, b):
    return a + b

# 调用：按顺序传参
result = add(1, 2)  # 3
```

### 2.2 默认参数（Default Arguments）

```python
def greet(name, greeting="Hello"):
    return f"{greeting}, {name}!"

# 调用
greet("Alice")              # "Hello, Alice!"
greet("Alice", "Hi")        # "Hi, Alice!"
greet("Alice", greeting="Hey")  # "Hey, Alice!"
```

**Java对照**：
```java
// Java: 不支持默认参数，需要重载
public String greet(String name) {
    return greet(name, "Hello");
}

public String greet(String name, String greeting) {
    return greeting + ", " + name + "!";
}
```

**⚠️ 陷阱：默认参数必须在最后**
```python
# ❌ 错误：默认参数后面不能有非默认参数
def greet(greeting="Hello", name):  # SyntaxError
    pass

# ✅ 正确
def greet(name, greeting="Hello"):
    pass
```

### 2.3 关键字参数（Keyword Arguments）

```python
def register(name, age, city):
    return f"{name}, {age}岁, {city}"

# 方式1: 按位置
register("Alice", 25, "Beijing")

# 方式2: 按关键字（顺序无关）
register(city="Beijing", name="Alice", age=25)

# 方式3: 混合（位置参数必须在关键字参数前面）
register("Alice", age=25, city="Beijing")
```

### 2.4 *args — 可变位置参数

```python
def sum_all(*args):
    """接收任意数量的位置参数，args是一个tuple"""
    print(f"args = {args}")  # args = (1, 2, 3)
    return sum(args)

# 调用
sum_all(1, 2, 3)        # 6
sum_all(1, 2, 3, 4, 5)  # 15
```

**Java对照**：
```java
// Java: 可变参数
public int sumAll(int... args) {
    return Arrays.stream(args).sum();
}
```

**实际应用**：
```python
def log(level, *messages):
    """日志函数"""
    msg = " ".join(str(m) for m in messages)
    print(f"[{level}] {msg}")

log("INFO", "User", "Alice", "logged in")  # [INFO] User Alice logged in
log("ERROR", "Connection", "failed", 404)  # [ERROR] Connection failed 404
```

### 2.5 **kwargs — 可变关键字参数

```python
def print_info(**kwargs):
    """接收任意数量的关键字参数，kwargs是一个dict"""
    print(f"kwargs = {kwargs}")
    for key, value in kwargs.items():
        print(f"{key}: {value}")

# 调用
print_info(name="Alice", age=25, city="Beijing")
# kwargs = {'name': 'Alice', 'age': 25, 'city': 'Beijing'}
# name: Alice
# age: 25
# city: Beijing
```

**实际应用**：
```python
def create_user(name, **kwargs):
    """创建用户，kwargs存储可选字段"""
    user = {"name": name}
    user.update(kwargs)
    return user

user = create_user("Alice", age=25, email="alice@example.com")
# {'name': 'Alice', 'age': 25, 'email': 'alice@example.com'}
```

### 2.6 参数组合（完整规则）

```python
def func(pos_only, /, normal, *, kw_only):
    """
    参数顺序规则：
    1. pos_only: 只能按位置传参（/前面）
    2. normal: 位置或关键字都可以
    3. kw_only: 只能按关键字传参（*后面）
    """
    pass

# 调用示例
func(1, 2, kw_only=3)      # ✅ 正确
func(1, normal=2, kw_only=3)  # ✅ 正确
func(pos_only=1, normal=2, kw_only=3)  # ❌ 错误：pos_only不能用关键字
```

### 2.7 解包参数

```python
def add(a, b, c):
    return a + b + c

# 解包list/tuple
args = [1, 2, 3]
add(*args)  # 等价于 add(1, 2, 3)

# 解包dict
kwargs = {"a": 1, "b": 2, "c": 3}
add(**kwargs)  # 等价于 add(a=1, b=2, c=3)
```

---

## 🔧 第三部分：lambda函数

### 3.1 什么是lambda？

**lambda = 匿名函数 = 一次性的小函数**

```python
# 普通函数
def square(x):
    return x ** 2

# lambda函数（等价）
square = lambda x: x ** 2

# 调用
square(5)  # 25
```

### 3.2 Java对照

```java
// Java: Lambda表达式
Function<Integer, Integer> square = x -> x * x;

// 或者方法引用
Function<Integer, Integer> square = Math::pow;
```

### 3.3 lambda的使用场景

```python
# 场景1: 作为排序的key
students = [("Alice", 90), ("Bob", 80), ("Charlie", 95)]
students.sort(key=lambda s: s[1])  # 按成绩排序
# [("Bob", 80), ("Alice", 90), ("Charlie", 95)]

# 场景2: 作为filter的条件
numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
evens = list(filter(lambda x: x % 2 == 0, numbers))
# [2, 4, 6, 8, 10]

# 场景3: 作为map的转换
squares = list(map(lambda x: x ** 2, numbers))
# [1, 4, 9, 16, 25, 36, 49, 64, 81, 100]

# 场景4: 字典排序
scores = {"Alice": 90, "Bob": 80, "Charlie": 95}
sorted_scores = dict(sorted(scores.items(), key=lambda x: x[1], reverse=True))
# {"Charlie": 95, "Alice": 90, "Bob": 80}
```

**Java对照**：
```java
// Java: Stream + Lambda
List<Integer> evens = numbers.stream()
    .filter(x -> x % 2 == 0)
    .collect(Collectors.toList());

List<Integer> squares = numbers.stream()
    .map(x -> x * x)
    .collect(Collectors.toList());
```

---

## 🎨 第四部分：装饰器（Decorator）

### 4.1 什么是装饰器？

**装饰器 = Java的AOP（面向切面编程）**

在不修改原函数代码的情况下，给函数添加额外功能。

### 4.2 Java对照

```java
// Java: AOP切面
@Aspect
@Component
public class LogAspect {
    @Around("execution(* com.example.service.*.*(..))")
    public Object log(ProceedingJoinPoint point) throws Throwable {
        System.out.println("Before: " + point.getSignature().getName());
        Object result = point.proceed();
        System.out.println("After: " + point.getSignature().getName());
        return result;
    }
}
```

```python
# Python: 装饰器
def log_decorator(func):
    def wrapper(*args, **kwargs):
        print(f"Before: {func.__name__}")
        result = func(*args, **kwargs)
        print(f"After: {func.__name__}")
        return result
    return wrapper

@log_decorator
def say_hello(name):
    print(f"Hello, {name}!")

# 调用
say_hello("Alice")
# Before: say_hello
# Hello, Alice!
# After: say_hello
```

### 4.3 装饰器的工作原理

```python
# @log_decorator 等价于：
say_hello = log_decorator(say_hello)
```

### 4.4 带参数的装饰器

```python
def repeat(n):
    """带参数的装饰器：重复执行n次"""
    def decorator(func):
        def wrapper(*args, **kwargs):
            for _ in range(n):
                result = func(*args, **kwargs)
            return result
        return wrapper
    return decorator

@repeat(3)
def say_hello(name):
    print(f"Hello, {name}!")

say_hello("Alice")
# Hello, Alice!
# Hello, Alice!
# Hello, Alice!
```

### 4.5 保留原函数信息

```python
from functools import wraps

def log_decorator(func):
    @wraps(func)  # 保留原函数的__name__、__doc__等
    def wrapper(*args, **kwargs):
        print(f"Calling {func.__name__}")
        return func(*args, **kwargs)
    return wrapper

@log_decorator
def greet(name):
    """问候函数"""
    return f"Hello, {name}!"

print(greet.__name__)  # "greet"（不是"wrapper"）
print(greet.__doc__)   # "问候函数"
```

### 4.6 常用内置装饰器

```python
# 1. @staticmethod — 静态方法
class MathUtils:
    @staticmethod
    def add(a, b):
        return a + b

MathUtils.add(1, 2)  # 不需要实例化

# 2. @classmethod — 类方法
class User:
    count = 0
    
    @classmethod
    def increment(cls):
        cls.count += 1
    
    @classmethod
    def get_count(cls):
        return cls.count

User.increment()
print(User.get_count())  # 1

# 3. @property — 属性（getter）
class Circle:
    def __init__(self, radius):
        self._radius = radius
    
    @property
    def radius(self):
        return self._radius
    
    @property
    def area(self):
        return 3.14 * self._radius ** 2

c = Circle(5)
print(c.radius)  # 5（不需要括号）
print(c.area)    # 78.5（不需要括号）

# 4. @functools.lru_cache — 缓存（记忆化）
from functools import lru_cache

@lru_cache(maxsize=128)
def fibonacci(n):
    if n < 2:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

fibonacci(100)  # 快速计算，因为有缓存
```

### 4.7 实际应用：项目中的装饰器

```python
# aipy2/app/core/logger.py

import time
from functools import wraps

def log_execution_time(func):
    """记录函数执行时间的装饰器"""
    @wraps(func)
    async def wrapper(*args, **kwargs):
        start = time.time()
        result = await func(*args, **kwargs)
        end = time.time()
        print(f"{func.__name__} took {end - start:.2f}s")
        return result
    return wrapper

# 使用
@log_execution_time
async def search_node(state):
    """检索节点"""
    # 执行检索逻辑
    return results
```

---

## 🔄 第五部分：高阶函数

### 5.1 什么是高阶函数？

**高阶函数 = 接收函数作为参数 或 返回函数的函数**

### 5.2 map — 映射

```python
numbers = [1, 2, 3, 4, 5]

# map(函数, 可迭代对象)
squared = list(map(lambda x: x ** 2, numbers))
# [1, 4, 9, 16, 25]

# 等价于列表推导式（更Pythonic）
squared = [x ** 2 for x in numbers]
```

### 5.3 filter — 过滤

```python
numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

# filter(函数, 可迭代对象)
evens = list(filter(lambda x: x % 2 == 0, numbers))
# [2, 4, 6, 8, 10]

# 等价于列表推导式
evens = [x for x in numbers if x % 2 == 0]
```

### 5.4 reduce — 累积

```python
from functools import reduce

numbers = [1, 2, 3, 4, 5]

# reduce(函数, 可迭代对象, 初始值)
total = reduce(lambda acc, x: acc + x, numbers, 0)
# 15

# 等价于sum()
total = sum(numbers)
```

### 5.5 sorted — 排序

```python
students = [("Alice", 90), ("Bob", 80), ("Charlie", 95)]

# sorted(可迭代对象, key=函数, reverse=bool)
sorted_students = sorted(students, key=lambda s: s[1], reverse=True)
# [("Charlie", 95), ("Alice", 90), ("Bob", 80)]
```

---

## 🎯 第六部分：面试速记

### Q1: *args和**kwargs的区别？
**A**: `*args`接收任意数量的位置参数，存为tuple；`**kwargs`接收任意数量的关键字参数，存为dict。参数顺序：位置参数 → *args → 关键字参数 → **kwargs。

### Q2: 什么是装饰器？
**A**: 装饰器是Python的AOP，用@语法给函数添加额外功能，不修改原代码。本质是接收函数作为参数并返回新函数的高阶函数。

### Q3: lambda和普通函数的区别？
**A**: lambda是匿名函数，只能写一行表达式，没有函数名。适合简单的、一次性的小函数，通常作为参数传给高阶函数。

### Q4: 默认参数的陷阱？
**A**: 默认参数只在函数定义时计算一次。如果默认值是可变对象（如list），多次调用会共享同一个对象。解决方案：用None作为默认值，在函数内部创建新对象。

### Q5: 什么是闭包？
**A**: 闭包是内部函数引用了外部函数的变量，即使外部函数执行完毕，内部函数仍然能访问那些变量。装饰器就是闭包的典型应用。

---

## 🔗 相关笔记

- [[14_Python数据结构详解]] — list/dict/tuple/set
- [[16_Python面向对象]] — 类和对象
- [[19_Python基础语法速成]] — Python基础语法

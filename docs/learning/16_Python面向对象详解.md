# 16_Python面向对象详解：类、继承、魔术方法的Java对照学习

> **面向对象**: 有Java背景、正在学习Python的开发者
> **核心目标**: 掌握Python面向对象的核心概念和API
> **学习价值**: 面向对象是编程基础，理解Python的鸭子类型很重要

---

## 📖 第一部分：类定义基础

### 1.1 基础语法

```python
class User:
    """用户类（这是docstring，类似Java的JavaDoc）"""
    
    # 类变量（类似Java的static变量）
    count = 0
    
    def __init__(self, name: str, age: int):
        """构造函数（类似Java的构造器）"""
        # 实例变量（类似Java的成员变量）
        self.name = name
        self.age = age
        User.count += 1
    
    def greet(self) -> str:
        """实例方法（第一个参数必须是self）"""
        return f"Hello, I'm {self.name}, {self.age} years old."
    
    @classmethod
    def get_count(cls) -> int:
        """类方法（类似Java的static方法）"""
        return cls.count
    
    @staticmethod
    def is_adult(age: int) -> bool:
        """静态方法（不需要self或cls）"""
        return age >= 18

# 使用
user = User("Alice", 25)
print(user.greet())        # "Hello, I'm Alice, 25 years old."
print(User.get_count())    # 1
print(User.is_adult(25))   # True
```

### 1.2 Java对照

```java
// Java
public class User {
    // 类变量
    private static int count = 0;
    
    // 实例变量
    private String name;
    private int age;
    
    // 构造器
    public User(String name, int age) {
        this.name = name;
        this.age = age;
        count++;
    }
    
    // 实例方法
    public String greet() {
        return "Hello, I'm " + name + ", " + age + " years old.";
    }
    
    // 静态方法
    public static int getCount() {
        return count;
    }
    
    // 静态方法
    public static boolean isAdult(int age) {
        return age >= 18;
    }
}
```

### 1.3 关键区别

| 特性 | Java | Python |
|------|------|--------|
| 构造器 | `public User()` | `def __init__(self)` |
| 实例变量 | `this.name` | `self.name` |
| 类变量 | `static int count` | `count = 0`（类体中） |
| 实例方法 | `public void method()` | `def method(self)` |
| 类方法 | `static void method()` | `@classmethod def method(cls)` |
| 静态方法 | `static void method()` | `@staticmethod def method()` |
| 访问控制 | `private/protected/public` | `_`前缀（约定） |

---

## 📦 第二部分：访问控制

### 2.1 Python的访问控制（约定，非强制）

```python
class User:
    def __init__(self, name, age, password):
        self.name = name          # 公开（public）
        self._age = age           # 保护（protected，约定）
        self.__password = password # 私有（private，名称改写）
    
    def get_password(self):
        """访问私有变量"""
        return self.__password

user = User("Alice", 25, "secret")
print(user.name)        # ✅ 可以访问
print(user._age)        # ⚠️ 可以访问（但不建议）
# print(user.__password) # ❌ AttributeError
print(user.get_password())  # ✅ 通过方法访问
print(user._User__password)  # ⚠️ 可以强制访问（不推荐）
```

### 2.2 Java对照

```java
// Java: 严格的访问控制
public class User {
    public String name;        // 公开
    protected int age;         // 保护
    private String password;   // 私有
    
    public String getPassword() {
        return password;
    }
}
```

**关键区别**：Python的访问控制是**约定**，不是强制。`_`前缀只是告诉开发者"这是内部使用的"，但技术上仍然可以访问。

---

## 🔧 第三部分：属性（Property）

### 3.1 什么是Property？

**Property = Java的getter/setter**

用`@property`装饰器实现，让方法调用看起来像属性访问。

### 3.2 Java对照

```java
// Java: getter/setter
public class User {
    private String name;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}

// 使用
user.getName();
user.setName("Alice");
```

```python
# Python: @property
class User:
    def __init__(self, name):
        self._name = name
    
    @property
    def name(self):
        """getter"""
        return self._name
    
    @name.setter
    def name(self, value):
        """setter"""
        if not value:
            raise ValueError("Name cannot be empty")
        self._name = value

# 使用（看起来像属性访问）
user = User("Alice")
print(user.name)    # 调用getter
user.name = "Bob"   # 调用setter
```

### 3.3 只读属性

```python
class Circle:
    def __init__(self, radius):
        self._radius = radius
    
    @property
    def radius(self):
        return self._radius
    
    @property
    def area(self):
        """只读属性（没有setter）"""
        return 3.14 * self._radius ** 2
    
    @property
    def circumference(self):
        """只读属性"""
        return 2 * 3.14 * self._radius

c = Circle(5)
print(c.radius)        # 5
print(c.area)          # 78.5
print(c.circumference) # 31.4

# c.area = 100  # ❌ AttributeError: can't set attribute
```

### 3.4 计算属性

```python
class User:
    def __init__(self, first_name, last_name):
        self.first_name = first_name
        self.last_name = last_name
    
    @property
    def full_name(self):
        """计算属性（每次访问时计算）"""
        return f"{self.first_name} {self.last_name}"

user = User("Alice", "Smith")
print(user.full_name)  # "Alice Smith"

user.first_name = "Bob"
print(user.full_name)  # "Bob Smith"（自动更新）
```

---

## 🎭 第四部分：魔术方法（Dunder Methods）

### 4.1 什么是魔术方法？

**魔术方法 = Java的特殊接口方法**

以`__`开头和结尾的方法，Python在特定场景自动调用。

### 4.2 常用魔术方法

#### `__str__` 和 `__repr__`

```python
class User:
    def __init__(self, name, age):
        self.name = name
        self.age = age
    
    def __str__(self):
        """用户友好的字符串（print时调用）"""
        return f"User({self.name}, {self.age})"
    
    def __repr__(self):
        """开发者友好的字符串（调试时调用）"""
        return f"User(name='{self.name}', age={self.age})"

user = User("Alice", 25)
print(user)          # 调用__str__: User(Alice, 25)
print(repr(user))    # 调用__repr__: User(name='Alice', age=25)
```

**Java对照**：
```java
// Java: toString()
@Override
public String toString() {
    return "User{" + "name='" + name + "', age=" + age + "}";
}
```

#### `__eq__` 和 `__hash__`

```python
class User:
    def __init__(self, id, name):
        self.id = id
        self.name = name
    
    def __eq__(self, other):
        """相等比较（==）"""
        if not isinstance(other, User):
            return False
        return self.id == other.id
    
    def __hash__(self):
        """哈希值（用作dict的key或set的元素）"""
        return hash(self.id)

user1 = User(1, "Alice")
user2 = User(1, "Bob")
user3 = User(2, "Alice")

print(user1 == user2)  # True（id相同）
print(user1 == user3)  # False（id不同）

# 可以用作set的元素
users = {user1, user2, user3}
print(len(users))  # 2（user1和user2被认为是同一个）
```

**Java对照**：
```java
// Java: equals()和hashCode()
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return id == user.id;
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
```

#### `__len__` 和 `__getitem__`

```python
class Team:
    def __init__(self, members):
        self.members = members
    
    def __len__(self):
        """len()函数调用"""
        return len(self.members)
    
    def __getitem__(self, index):
        """索引访问（team[0]）"""
        return self.members[index]
    
    def __contains__(self, item):
        """in运算符"""
        return item in self.members

team = Team(["Alice", "Bob", "Charlie"])
print(len(team))      # 3（调用__len__）
print(team[0])        # "Alice"（调用__getitem__）
print("Alice" in team) # True（调用__contains__）
```

#### `__call__`

```python
class Multiplier:
    def __init__(self, factor):
        self.factor = factor
    
    def __call__(self, x):
        """让实例可以像函数一样调用"""
        return x * self.factor

double = Multiplier(2)
triple = Multiplier(3)

print(double(5))   # 10
print(triple(5))   # 15

# 等价于
print(double.__call__(5))  # 10
```

#### `__enter__` 和 `__exit__`（上下文管理器）

```python
class FileManager:
    def __init__(self, filename, mode):
        self.filename = filename
        self.mode = mode
        self.file = None
    
    def __enter__(self):
        """进入with语句时调用"""
        self.file = open(self.filename, self.mode)
        return self.file
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """离开with语句时调用（无论是否异常）"""
        if self.file:
            self.file.close()

# 使用
with FileManager("test.txt", "w") as f:
    f.write("Hello, World!")
# 文件自动关闭
```

**Java对照**：
```java
// Java: try-with-resources
try (FileWriter fw = new FileWriter("test.txt")) {
    fw.write("Hello, World!");
}
// 自动关闭
```

### 4.3 魔术方法速查表

| 方法 | 触发场景 | Java对应 |
|------|---------|----------|
| `__init__` | 创建实例 | 构造器 |
| `__str__` | `str(obj)` / `print(obj)` | `toString()` |
| `__repr__` | `repr(obj)` / 调试 | `toString()` |
| `__eq__` | `obj1 == obj2` | `equals()` |
| `__hash__` | `hash(obj)` / dict key | `hashCode()` |
| `__len__` | `len(obj)` | `size()` |
| `__getitem__` | `obj[key]` | `get(index)` |
| `__setitem__` | `obj[key] = value` | `set(index, value)` |
| `__contains__` | `item in obj` | `contains()` |
| `__iter__` | `for item in obj` | `iterator()` |
| `__call__` | `obj()` | 函数式接口 |
| `__enter__` | `with obj:` | `AutoCloseable` |
| `__exit__` | 离开with | `close()` |
| `__add__` | `obj1 + obj2` | 重载`+` |
| `__lt__` | `obj1 < obj2` | `Comparable` |

---

## 🏗️ 第五部分：继承

### 5.1 基础继承

```python
class Animal:
    def __init__(self, name):
        self.name = name
    
    def speak(self):
        raise NotImplementedError("Subclass must implement this")

class Dog(Animal):
    def speak(self):
        return f"{self.name}: Woof!"

class Cat(Animal):
    def speak(self):
        return f"{self.name}: Meow!"

dog = Dog("Buddy")
cat = Cat("Whiskers")

print(dog.speak())  # "Buddy: Woof!"
print(cat.speak())  # "Whiskers: Meow!"
```

**Java对照**：
```java
// Java
public abstract class Animal {
    protected String name;
    
    public Animal(String name) {
        this.name = name;
    }
    
    public abstract String speak();
}

public class Dog extends Animal {
    public Dog(String name) {
        super(name);
    }
    
    @Override
    public String speak() {
        return name + ": Woof!";
    }
}
```

### 5.2 多继承（Python特有）

```python
class Flyable:
    def fly(self):
        return "I can fly"

class Swimmable:
    def swim(self):
        return "I can swim"

class Duck(Flyable, Swimmable):
    """鸭子：会飞会游泳"""
    pass

duck = Duck()
print(duck.fly())   # "I can fly"
print(duck.swim())  # "I can swim"
```

**Java对照**：
```java
// Java: 接口实现
public interface Flyable {
    String fly();
}

public interface Swimmable {
    String swim();
}

public class Duck implements Flyable, Swimmable {
    @Override
    public String fly() { return "I can fly"; }
    
    @Override
    public String swim() { return "I can swim"; }
}
```

### 5.3 MRO（方法解析顺序）

```python
class A:
    def method(self):
        return "A"

class B(A):
    def method(self):
        return "B"

class C(A):
    def method(self):
        return "C"

class D(B, C):
    pass

d = D()
print(d.method())  # "B"（按照MRO顺序：D -> B -> C -> A）
print(D.__mro__)   # (<class 'D'>, <class 'B'>, <class 'C'>, <class 'A'>, <class 'object'>)
```

### 5.4 super()函数

```python
class Animal:
    def __init__(self, name):
        self.name = name
        print(f"Animal.__init__: {name}")

class Dog(Animal):
    def __init__(self, name, breed):
        super().__init__(name)  # 调用父类构造器
        self.breed = breed
        print(f"Dog.__init__: {breed}")

dog = Dog("Buddy", "Golden Retriever")
# Animal.__init__: Buddy
# Dog.__init__: Golden Retriever
```

---

## 🎯 第六部分：鸭子类型（Duck Typing）

### 6.1 什么是鸭子类型？

**"如果它走起来像鸭子，叫起来像鸭子，那它就是鸭子。"**

Python不关心对象的类型，只关心对象有什么方法。

### 6.2 示例

```python
class Duck:
    def speak(self):
        return "Quack!"
    
    def walk(self):
        return "Walking like a duck"

class Dog:
    def speak(self):
        return "Woof!"
    
    def walk(self):
        return "Walking like a dog"

class Cat:
    def speak(self):
        return "Meow!"
    
    def walk(self):
        return "Walking like a cat"

# 不关心类型，只关心有没有speak和walk方法
def animal_actions(animal):
    print(animal.speak())
    print(animal.walk())

# 都可以传入
animal_actions(Duck())
animal_actions(Dog())
animal_actions(Cat())
```

**Java对照**：
```java
// Java: 需要接口或继承
public interface Animal {
    String speak();
    String walk();
}

public class Duck implements Animal { ... }
public class Dog implements Animal { ... }

// 必须声明类型
public void animalActions(Animal animal) {
    System.out.println(animal.speak());
    System.out.println(animal.walk());
}
```

### 6.3 Protocol（Python 3.8+，类似接口）

```python
from typing import Protocol

class Speakable(Protocol):
    """可说话的协议（类似Java接口）"""
    def speak(self) -> str: ...

class Walkable(Protocol):
    """可走路的协议"""
    def walk(self) -> str: ...

# 不需要显式实现接口
class Duck:
    def speak(self) -> str:
        return "Quack!"
    
    def walk(self) -> str:
        return "Walking like a duck"

# Duck自动满足Speakable和Walkable协议
def make_speak(obj: Speakable) -> str:
    return obj.speak()

make_speak(Duck())  # ✅ 正确
```

---

## 🎯 第七部分：面试速记

### Q1: Python的self是什么？
**A**: self是实例方法的第一个参数，代表实例本身。类似Java的`this`，但Python必须显式声明。调用时不需要传入，Python自动传入。

### Q2: @property的作用？
**A**: 让方法调用看起来像属性访问。实现getter/setter，可以添加验证逻辑。只读属性只定义getter不定义setter。

### Q3: 什么是魔术方法？
**A**: 以`__`开头和结尾的方法，Python在特定场景自动调用。如`__init__`（构造）、`__str__`（字符串表示）、`__eq__`（相等比较）。

### Q4: Python支持多继承吗？
**A**: 支持，Java不支持类的多继承（只支持接口）。Python用MRO（方法解析顺序）解决菱形继承问题。

### Q5: 什么是鸭子类型？
**A**: Python不关心对象的类型，只关心对象有什么方法。只要对象有需要的方法，就可以使用。这是Python的核心设计哲学。

### Q6: __str__和__repr__的区别？
**A**: `__str__`是用户友好的字符串，print时调用；`__repr__`是开发者友好的字符串，调试时调用。如果没有`__str__`，会回退到`__repr__`。

---

## 🔗 相关笔记

- [[14_Python数据结构详解]] — list/dict/tuple/set
- [[15_Python函数详解]] — 函数定义和参数
- [[19_Python基础语法速成]] — Python基础语法

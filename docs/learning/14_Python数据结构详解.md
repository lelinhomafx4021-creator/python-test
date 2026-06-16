# 14_Python数据结构详解：list、dict、tuple、set的Java对照学习

> **面向对象**: 有Java背景、正在学习Python的开发者
> **核心目标**: 掌握Python四大内置数据结构的API用法
> **学习价值**: 数据结构是编程基础，面试必问

---

## 📖 第一部分：list（列表）

### 1.1 什么是list？

**Python的list = Java的ArrayList**

有序、可重复、可变的集合。

### 1.2 Java对照

```java
// Java: ArrayList
List<String> list = new ArrayList<>();
list.add("a");
list.add("b");
String first = list.get(0);
int size = list.size();
```

```python
# Python: list
list = ["a", "b"]
first = list[0]
size = len(list)
```

### 1.3 关键API详解

#### API 1: 创建list

```python
# 方式1: 字面量
list1 = [1, 2, 3, "hello", True]  # 可以混合类型

# 方式2: 构造函数
list2 = list()  # 空列表
list3 = list("abc")  # ['a', 'b', 'c']
list4 = list(range(5))  # [0, 1, 2, 3, 4]

# 方式3: 列表推导式（Python特有，非常常用）
squares = [x**2 for x in range(5)]  # [0, 1, 4, 9, 16]
evens = [x for x in range(10) if x % 2 == 0]  # [0, 2, 4, 6, 8]
```

**Java对照**：
```java
// Java: 没有列表推导式，需要用Stream
List<Integer> squares = IntStream.range(0, 5)
    .map(x -> x * x)
    .boxed()
    .collect(Collectors.toList());
```

#### API 2: 访问元素

```python
list = [10, 20, 30, 40, 50]

# 正向索引（从0开始）
first = list[0]      # 10
second = list[1]     # 20

# 负向索引（从末尾开始，-1是最后一个）
last = list[-1]      # 50
second_last = list[-2]  # 40

# 切片（Python特有，非常强大）
sub = list[1:3]      # [20, 30]（左闭右开）
sub = list[:3]       # [10, 20, 30]（从开头）
sub = list[2:]       # [30, 40, 50]（到末尾）
sub = list[::2]      # [10, 30, 50]（步长为2）
sub = list[::-1]     # [50, 40, 30, 20, 10]（反转）
```

**Java对照**：
```java
// Java: 没有切片语法，需要用subList
List<Integer> sub = list.subList(1, 3);  // [20, 30]

// 反转需要额外操作
Collections.reverse(list);
```

#### API 3: 添加元素

```python
list = [1, 2, 3]

# append: 末尾添加（最常用）
list.append(4)        # [1, 2, 3, 4]

# insert: 指定位置插入
list.insert(0, 0)     # [0, 1, 2, 3, 4]
list.insert(2, 99)    # [0, 1, 99, 2, 3, 4]

# extend: 合并列表
list.extend([5, 6])   # [0, 1, 99, 2, 3, 4, 5, 6]

# + 运算符: 创建新列表
new = [1, 2] + [3, 4]  # [1, 2, 3, 4]

# * 运算符: 重复
repeated = [0] * 5     # [0, 0, 0, 0, 0]
```

**Java对照**：
```java
// Java
list.add(4);           // append
list.add(0, 0);        // insert
list.addAll(otherList); // extend
```

#### API 4: 删除元素

```python
list = [1, 2, 3, 4, 5, 3]

# remove: 删除第一个匹配项
list.remove(3)        # [1, 2, 4, 5, 3]

# pop: 删除并返回指定位置（默认最后一个）
last = list.pop()     # 返回3，list变成[1, 2, 4, 5]
first = list.pop(0)   # 返回1，list变成[2, 4, 5]

# del: 删除指定位置
del list[0]           # [4, 5]

# clear: 清空
list.clear()          # []
```

**Java对照**：
```java
// Java
list.remove(Integer.valueOf(3));  // remove by value
list.remove(0);                   // remove by index
list.clear();                     // clear
```

#### API 5: 查找和统计

```python
list = [1, 2, 3, 2, 4, 2]

# in: 判断元素是否存在
exists = 2 in list      # True
not_exists = 99 in list  # False

# index: 查找元素位置（不存在会报错）
idx = list.index(2)      # 1（第一个2的位置）
idx = list.index(2, 2)   # 3（从位置2开始找）

# count: 统计出现次数
cnt = list.count(2)      # 3

# len: 长度
size = len(list)         # 6
```

**Java对照**：
```java
// Java
boolean exists = list.contains(2);
int idx = list.indexOf(2);
long cnt = list.stream().filter(x -> x == 2).count();
int size = list.size();
```

#### API 6: 排序和反转

```python
list = [3, 1, 4, 1, 5, 9, 2, 6]

# sort: 原地排序（修改原列表）
list.sort()              # [1, 1, 2, 3, 4, 5, 6, 9]
list.sort(reverse=True)  # [9, 6, 5, 4, 3, 2, 1, 1]

# sorted: 返回新列表（不修改原列表）
new_list = sorted(list)          # [1, 1, 2, 3, 4, 5, 6, 9]
new_list = sorted(list, reverse=True)  # [9, 6, 5, 4, 3, 2, 1, 1]

# reverse: 原地反转
list.reverse()           # [6, 2, 9, 5, 4, 3, 1, 1]

# reversed: 返回迭代器
for item in reversed(list):
    print(item)
```

**Java对照**：
```java
// Java
Collections.sort(list);
Collections.reverse(list);
List<Integer> sorted = list.stream().sorted().collect(Collectors.toList());
```

#### API 7: 列表推导式（Python特有）

```python
# 基础语法: [表达式 for 变量 in 可迭代对象]
squares = [x**2 for x in range(5)]  # [0, 1, 4, 9, 16]

# 带条件: [表达式 for 变量 in 可迭代对象 if 条件]
evens = [x for x in range(10) if x % 2 == 0]  # [0, 2, 4, 6, 8]

# 嵌套循环: [表达式 for 变量1 in 可迭代1 for 变量2 in 可迭代2]
pairs = [(x, y) for x in range(3) for y in range(3)]
# [(0,0), (0,1), (0,2), (1,0), (1,1), (1,2), (2,0), (2,1), (2,2)]

# 实际应用
words = ["hello", "world", "python"]
upper_words = [w.upper() for w in words]  # ["HELLO", "WORLD", "PYTHON"]
long_words = [w for w in words if len(w) > 5]  # ["python"]
```

**Java对照**：
```java
// Java: Stream API
List<Integer> squares = IntStream.range(0, 5)
    .map(x -> x * x)
    .boxed()
    .collect(Collectors.toList());

List<String> upperWords = words.stream()
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

---

## 📦 第二部分：dict（字典）

### 2.1 什么是dict？

**Python的dict = Java的HashMap**

键值对、无序（Python 3.7+有序）、可变。

### 2.2 Java对照

```java
// Java: HashMap
Map<String, Object> map = new HashMap<>();
map.put("name", "Alice");
map.put("age", 25);
String name = (String) map.get("name");
boolean exists = map.containsKey("name");
```

```python
# Python: dict
dict = {"name": "Alice", "age": 25}
name = dict["name"]
exists = "name" in dict
```

### 2.3 关键API详解

#### API 1: 创建dict

```python
# 方式1: 字面量（最常用）
dict1 = {"name": "Alice", "age": 25}

# 方式2: dict()构造函数
dict2 = dict(name="Alice", age=25)
dict3 = dict([("name", "Alice"), ("age", 25)])  # 从元组列表创建

# 方式3: 字典推导式（Python特有）
squares = {x: x**2 for x in range(5)}  # {0:0, 1:1, 2:4, 3:9, 4:16}

# 方式4: fromkeys（批量创建相同值）
keys = ["a", "b", "c"]
dict4 = dict.fromkeys(keys, 0)  # {"a":0, "b":0, "c":0}
```

#### API 2: 访问元素

```python
dict = {"name": "Alice", "age": 25, "city": "Beijing"}

# 方式1: []（不存在会报错）
name = dict["name"]      # "Alice"

# 方式2: get()（不存在返回默认值，推荐）
name = dict.get("name")          # "Alice"
phone = dict.get("phone")        # None
phone = dict.get("phone", "N/A") # "N/A"（默认值）

# 方式3: keys(), values(), items()
keys = dict.keys()       # dict_keys(["name", "age", "city"])
values = dict.values()   # dict_values(["Alice", 25, "Beijing"])
items = dict.items()     # dict_items([("name","Alice"), ("age",25), ("city","Beijing")])

# 遍历
for key in dict:
    print(key, dict[key])

for key, value in dict.items():
    print(key, value)
```

**Java对照**：
```java
// Java
String name = map.get("name");
String phone = map.getOrDefault("phone", "N/A");

for (Map.Entry<String, Object> entry : map.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

#### API 3: 添加/修改元素

```python
dict = {"name": "Alice"}

# 添加/修改: []赋值
dict["age"] = 25         # 添加
dict["age"] = 26         # 修改

# update: 批量更新
dict.update({"city": "Beijing", "age": 27})

# setdefault: 不存在则添加，存在则返回
dict.setdefault("phone", "123456")  # 添加phone
dict.setdefault("phone", "999999")  # phone已存在，不变
```

**Java对照**：
```java
// Java
map.put("age", 25);
map.putAll(otherMap);
map.putIfAbsent("phone", "123456");
```

#### API 4: 删除元素

```python
dict = {"name": "Alice", "age": 25, "city": "Beijing"}

# del: 删除指定键
del dict["age"]

# pop: 删除并返回
city = dict.pop("city")      # 返回"Beijing"
phone = dict.pop("phone", None)  # 不存在返回None，不报错

# popitem: 删除最后一个键值对
last = dict.popitem()        # ("name", "Alice")

# clear: 清空
dict.clear()
```

**Java对照**：
```java
// Java
map.remove("age");
String city = map.remove("city");
String phone = map.remove("phone");
map.clear();
```

#### API 5: 查找和判断

```python
dict = {"name": "Alice", "age": 25}

# in: 判断键是否存在
exists = "name" in dict       # True
not_exists = "phone" in dict  # False

# len: 键值对数量
size = len(dict)              # 2

# get + 条件判断
if "phone" in dict:
    phone = dict["phone"]
else:
    phone = "N/A"

# 等价于（更Pythonic）
phone = dict.get("phone", "N/A")
```

#### API 6: 字典推导式（Python特有）

```python
# 基础语法: {key_expr: value_expr for item in iterable}
squares = {x: x**2 for x in range(5)}  # {0:0, 1:1, 2:4, 3:9, 4:16}

# 带条件
even_squares = {x: x**2 for x in range(10) if x % 2 == 0}
# {0:0, 2:4, 4:16, 6:36, 8:64}

# 实际应用：反转字典
original = {"a": 1, "b": 2, "c": 3}
reversed_dict = {v: k for k, v in original.items()}  # {1:"a", 2:"b", 3:"c"}

# 过滤字典
scores = {"Alice": 90, "Bob": 60, "Charlie": 80, "David": 50}
passed = {name: score for name, score in scores.items() if score >= 60}
# {"Alice": 90, "Bob": 60, "Charlie": 80}
```

---

## 📌 第三部分：tuple（元组）

### 3.1 什么是tuple？

**Python的tuple = Java的不可变List**

有序、可重复、**不可变**（创建后不能修改）。

### 3.2 为什么需要tuple？

1. **安全性**: 不可变，防止意外修改
2. **性能**: 比list更快
3. **字典键**: 可以作为dict的key（list不行）
4. **函数返回值**: 返回多个值时用tuple

### 3.3 关键API详解

#### API 1: 创建tuple

```python
# 方式1: 字面量
tuple1 = (1, 2, 3)

# 方式2: 构造函数
tuple2 = tuple([1, 2, 3])  # 从list创建

# 方式3: 单元素tuple（注意逗号！）
single = (1,)      # tuple
not_tuple = (1)    # int（这是陷阱！）

# 方式4: 解包赋值
a, b, c = (1, 2, 3)  # a=1, b=2, c=3
```

#### API 2: 访问元素

```python
tuple = (10, 20, 30, 40, 50)

# 索引（和list一样）
first = tuple[0]     # 10
last = tuple[-1]     # 50

# 切片（和list一样）
sub = tuple[1:3]     # (20, 30)

# 遍历
for item in tuple:
    print(item)
```

#### API 3: 不可变特性

```python
tuple = (1, 2, 3)

# ❌ 不能修改
tuple[0] = 99        # TypeError
tuple.append(4)      # AttributeError

# ✅ 只能读取
first = tuple[0]
size = len(tuple)
exists = 2 in tuple
```

#### API 4: tuple的方法

```python
tuple = (1, 2, 3, 2, 4, 2)

# count: 统计出现次数
cnt = tuple.count(2)  # 3

# index: 查找位置
idx = tuple.index(2)  # 1（第一个2的位置）
```

#### API 5: tuple的实际应用

```python
# 1. 函数返回多个值
def get_user():
    return "Alice", 25, "Beijing"  # 返回tuple

name, age, city = get_user()  # 解包

# 2. 作为dict的key
locations = {
    (39.9, 116.4): "Beijing",
    (31.2, 121.5): "Shanghai"
}

# 3. 交换变量（Python特有）
a, b = 1, 2
a, b = b, a  # a=2, b=1

# 4. 命名tuple（更清晰）
from collections import namedtuple
Point = namedtuple("Point", ["x", "y"])
p = Point(1, 2)
print(p.x, p.y)  # 1 2
```

---

## 📚 第四部分：set（集合）

### 4.1 什么是set？

**Python的set = Java的HashSet**

无序、不重复、可变。

### 4.2 关键API详解

#### API 1: 创建set

```python
# 方式1: 字面量
set1 = {1, 2, 3}

# 方式2: 构造函数
set2 = set([1, 2, 3, 2, 1])  # {1, 2, 3}（自动去重）

# 方式3: 集合推导式（Python特有）
set3 = {x**2 for x in range(5)}  # {0, 1, 4, 9, 16}

# 注意：空集合必须用set()，不能用{}
empty_set = set()      # set
not_set = {}           # dict（这是陷阱！）
```

#### API 2: 添加/删除元素

```python
set = {1, 2, 3}

# add: 添加单个元素
set.add(4)        # {1, 2, 3, 4}
set.add(2)        # {1, 2, 3, 4}（已存在，不变）

# update: 批量添加
set.update([5, 6])  # {1, 2, 3, 4, 5, 6}

# remove: 删除（不存在会报错）
set.remove(1)     # {2, 3, 4, 5, 6}

# discard: 删除（不存在不报错，推荐）
set.discard(99)   # 不存在，不报错

# pop: 随机删除并返回
item = set.pop()

# clear: 清空
set.clear()
```

#### API 3: 集合运算（Python特有，非常强大）

```python
set_a = {1, 2, 3, 4, 5}
set_b = {4, 5, 6, 7, 8}

# 并集（所有元素）
union = set_a | set_b          # {1, 2, 3, 4, 5, 6, 7, 8}
union = set_a.union(set_b)

# 交集（共同元素）
intersection = set_a & set_b   # {4, 5}
intersection = set_a.intersection(set_b)

# 差集（A有B没有）
difference = set_a - set_b     # {1, 2, 3}
difference = set_a.difference(set_b)

# 对称差集（只在一方的元素）
sym_diff = set_a ^ set_b       # {1, 2, 3, 6, 7, 8}
sym_diff = set_a.symmetric_difference(set_b)

# 子集/超集判断
is_subset = {1, 2} <= {1, 2, 3}     # True
is_superset = {1, 2, 3} >= {1, 2}   # True
```

**Java对照**：
```java
// Java
Set<Integer> union = new HashSet<>(setA);
union.addAll(setB);

Set<Integer> intersection = new HashSet<>(setA);
intersection.retainAll(setB);

Set<Integer> difference = new HashSet<>(setA);
difference.removeAll(setB);
```

#### API 4: 实际应用

```python
# 1. 去重
list = [1, 2, 3, 2, 1, 4, 5, 4]
unique = list(set(list))  # [1, 2, 3, 4, 5]（顺序可能变）

# 保持顺序去重
unique_ordered = list(dict.fromkeys(list))  # [1, 2, 3, 4, 5]

# 2. 快速判断是否存在（比list快）
valid_codes = {"200", "301", "302", "404", "500"}
if "200" in valid_codes:
    print("有效")

# 3. 找共同好友
my_friends = {"Alice", "Bob", "Charlie"}
your_friends = {"Bob", "David", "Charlie"}
common = my_friends & your_friends  # {"Bob", "Charlie"}

# 4. 找差异
only_my = my_friends - your_friends  # {"Alice"}
only_your = your_friends - my_friends  # {"David"}
```

---

## 🎯 第五部分：数据结构选择指南

| 需求 | 选择 | 原因 |
|------|------|------|
| 有序、可重复 | list | 保持插入顺序 |
| 键值对 | dict | 快速查找 |
| 不可变数据 | tuple | 防止意外修改 |
| 去重、集合运算 | set | 自动去重，运算快 |
| 频繁查找 | dict/set | O(1)时间复杂度 |
| 频繁插入/删除 | list | 尾部操作O(1) |

---

## 🎯 第六部分：面试速记

### Q1: list和tuple的区别？
**A**: list可变，tuple不可变。tuple可以作为dict的key，list不行。tuple比list更快。

### Q2: dict的查找时间复杂度？
**A**: O(1)，因为使用哈希表实现。list的查找是O(n)。

### Q3: 如何去重？
**A**: 使用set：`list(set(original_list))`。如果要保持顺序，用`list(dict.fromkeys(original_list))`。

### Q4: 列表推导式是什么？
**A**: Python特有语法，用一行代码创建列表。`[x**2 for x in range(5)]`等价于Java的Stream API。

### Q5: 什么时候用tuple而不是list？
**A**: 1）数据不需要修改时 2）需要作为dict的key时 3）函数返回多个值时 4）需要性能优化时。

---

## 🔗 相关笔记

- [[15_Python函数详解]] — 函数定义和参数
- [[16_Python面向对象]] — 类和对象
- [[19_Python基础语法速成]] — Python基础语法

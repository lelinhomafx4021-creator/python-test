# 18_Python文件IO与序列化详解：文件操作、JSON、pickle的Java对照学习

> **面向对象**: 有Java背景、正在学习Python的开发者
> **核心目标**: 掌握Python文件IO和序列化的核心API
> **学习价值**: 文件操作是基础，JSON处理是AI应用必备

---

## 📖 第一部分：文件操作基础

### 1.1 打开文件

```python
# 方式1: open()函数（基础）
file = open("data.txt", "r")  # 只读
content = file.read()
file.close()  # 必须手动关闭

# 方式2: with语句（推荐，自动关闭）
with open("data.txt", "r") as file:
    content = file.read()
# 文件自动关闭
```

### 1.2 Java对照

```java
// Java: try-with-resources
try (BufferedReader reader = new BufferedReader(new FileReader("data.txt"))) {
    String content = reader.readLine();
}
// 自动关闭
```

### 1.3 文件模式

| 模式 | 说明 | Java对应 |
|------|------|----------|
| `"r"` | 只读（默认） | `FileReader` |
| `"w"` | 写入（覆盖） | `FileWriter` |
| `"a"` | 追加 | `FileWriter(file, true)` |
| `"x"` | 创建（已存在则报错） | `new File()` + 检查 |
| `"b"` | 二进制模式 | `FileInputStream` |
| `"t"` | 文本模式（默认） | `BufferedReader` |
| `"r+"` | 读写 | `RandomAccessFile` |

### 1.4 读取文件

```python
with open("data.txt", "r", encoding="utf-8") as f:
    # 方式1: 读取全部内容
    content = f.read()
    
    # 方式2: 读取一行
    line = f.readline()
    
    # 方式3: 读取所有行（返回列表）
    lines = f.readlines()
    
    # 方式4: 逐行遍历（推荐，内存友好）
    for line in f:
        print(line.strip())
```

**Java对照**：
```java
// Java
String content = Files.readString(Path.of("data.txt"));
List<String> lines = Files.readAllLines(Path.of("data.txt"));

// 逐行读取
try (BufferedReader reader = Files.newBufferedReader(Path.of("data.txt"))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

### 1.5 写入文件

```python
# 方式1: write()写入字符串
with open("output.txt", "w", encoding="utf-8") as f:
    f.write("Hello, World!\n")
    f.write("第二行\n")

# 方式2: writelines()写入列表
lines = ["第一行\n", "第二行\n", "第三行\n"]
with open("output.txt", "w", encoding="utf-8") as f:
    f.writelines(lines)

# 方式3: print()写入
with open("output.txt", "w", encoding="utf-8") as f:
    print("Hello", file=f)
    print("World", file=f)
```

**Java对照**：
```java
// Java
Files.writeString(Path.of("output.txt"), "Hello, World!\n");

// 或
try (BufferedWriter writer = Files.newBufferedWriter(Path.of("output.txt"))) {
    writer.write("Hello, World!");
    writer.newLine();
}
```

### 1.6 追加模式

```python
with open("log.txt", "a", encoding="utf-8") as f:
    f.write("新日志\n")  # 追加到末尾
```

---

## 📦 第二部分：路径操作（pathlib）

### 2.1 pathlib基础

```python
from pathlib import Path

# 创建路径对象
path = Path("data") / "users" / "alice.txt"  # data/users/alice.txt

# 常用属性
print(path.name)       # "alice.txt"（文件名）
print(path.stem)       # "alice"（不含扩展名）
print(path.suffix)     # ".txt"（扩展名）
print(path.parent)     # Path("data/users")（父目录）
print(path.parts)      # ('data', 'users', 'alice.txt')

# 路径操作
path.exists()          # 是否存在
path.is_file()         # 是否是文件
path.is_dir()          # 是否是目录
path.absolute()        # 绝对路径
```

**Java对照**：
```java
// Java: Path
Path path = Path.of("data", "users", "alice.txt");

path.getFileName()     // "alice.txt"
path.getParent()       // "data/users"
path.toAbsolutePath()  // 绝对路径

Files.exists(path)
Files.isRegularFile(path)
Files.isDirectory(path)
```

### 2.2 文件操作

```python
from pathlib import Path

path = Path("data.txt")

# 读写
content = path.read_text(encoding="utf-8")
path.write_text("Hello", encoding="utf-8")

# 二进制
data = path.read_bytes()
path.write_bytes(b"Hello")

# 创建目录
Path("new_dir").mkdir(parents=True, exist_ok=True)

# 删除
path.unlink()  # 删除文件
Path("empty_dir").rmdir()  # 删除空目录

# 遍历目录
for item in Path(".").iterdir():
    print(item)

# 递归遍历
for item in Path(".").rglob("*.py"):
    print(item)
```

### 2.3 文件查找

```python
from pathlib import Path

# 查找所有Python文件
py_files = list(Path(".").rglob("*.py"))

# 查找特定文件
config = Path(".").rglob("config.yaml")

# 按条件过滤
large_files = [f for f in Path(".").rglob("*") if f.is_file() and f.stat().st_size > 1000000]
```

---

## 📄 第三部分：JSON处理

### 3.1 JSON基础

```python
import json

# Python对象 → JSON字符串
data = {"name": "Alice", "age": 25, "scores": [90, 85, 95]}
json_str = json.dumps(data, ensure_ascii=False, indent=2)
print(json_str)

# JSON字符串 → Python对象
parsed = json.loads(json_str)
print(parsed["name"])  # "Alice"
```

### 3.2 Java对照

```java
// Java: Jackson
ObjectMapper mapper = new ObjectMapper();

// Java对象 → JSON字符串
String json = mapper.writeValueAsString(data);

// JSON字符串 → Java对象
Map<String, Object> parsed = mapper.readValue(json, Map.class);
```

### 3.3 JSON文件操作

```python
import json
from pathlib import Path

# 写入JSON文件
data = {"name": "Alice", "age": 25}
Path("data.json").write_text(
    json.dumps(data, ensure_ascii=False, indent=2),
    encoding="utf-8"
)

# 或使用json.dump()直接写入
with open("data.json", "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

# 读取JSON文件
content = Path("data.json").read_text(encoding="utf-8")
data = json.loads(content)

# 或使用json.load()直接读取
with open("data.json", "r", encoding="utf-8") as f:
    data = json.load(f)
```

### 3.4 复杂对象序列化

```python
import json
from datetime import datetime, date

class User:
    def __init__(self, name, created_at):
        self.name = name
        self.created_at = created_at

# 自定义编码器
class UserEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, (datetime, date)):
            return obj.isoformat()
        if isinstance(obj, User):
            return {
                "name": obj.name,
                "created_at": obj.created_at
            }
        return super().default(obj)

# 使用
user = User("Alice", datetime.now())
json_str = json.dumps(user, cls=UserEncoder)
print(json_str)  # {"name": "Alice", "created_at": "2024-01-01T12:00:00"}

# 自定义解码器
def user_decoder(dct):
    if "created_at" in dct:
        dct["created_at"] = datetime.fromisoformat(dct["created_at"])
    return dct

parsed = json.loads(json_str, object_hook=user_decoder)
```

### 3.5 JSON Lines格式

```python
import json

# JSON Lines: 每行一个JSON对象
data = [
    {"name": "Alice", "age": 25},
    {"name": "Bob", "age": 30},
    {"name": "Charlie", "age": 35}
]

# 写入
with open("users.jsonl", "w", encoding="utf-8") as f:
    for item in data:
        f.write(json.dumps(item, ensure_ascii=False) + "\n")

# 读取
users = []
with open("users.jsonl", "r", encoding="utf-8") as f:
    for line in f:
        users.append(json.loads(line.strip()))
```

---

## 🔧 第四部分：pickle序列化

### 4.1 pickle基础

```python
import pickle

# Python对象 → 字节流
data = {"name": "Alice", "scores": [90, 85, 95]}
bytes_data = pickle.dumps(data)

# 字节流 → Python对象
parsed = pickle.loads(bytes_data)
print(parsed)  # {'name': 'Alice', 'scores': [90, 85, 95]}
```

### 4.2 pickle文件操作

```python
import pickle

# 写入pickle文件
data = {"name": "Alice", "scores": [90, 85, 95]}
with open("data.pkl", "wb") as f:
    pickle.dump(data, f)

# 读取pickle文件
with open("data.pkl", "rb") as f:
    data = pickle.load(f)
```

### 4.3 pickle vs JSON

| 特性 | pickle | JSON |
|------|--------|------|
| 格式 | 二进制 | 文本 |
| 可读性 | 不可读 | 可读 |
| 类型支持 | 几乎所有Python类型 | 基础类型 |
| 安全性 | 不安全（可能执行恶意代码） | 安全 |
| 跨语言 | 不支持 | 支持 |
| 用途 | Python内部缓存 | 数据交换 |

### 4.4 安全警告

```python
# ❌ 危险：不要加载不可信的pickle数据
# pickle.loads(untrusted_data)  # 可能执行恶意代码！

# ✅ 安全：只加载自己保存的数据
with open("my_data.pkl", "rb") as f:
    data = pickle.load(f)
```

---

## 📊 第五部分：CSV处理

### 5.1 csv模块

```python
import csv

# 读取CSV
with open("users.csv", "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    header = next(reader)  # 表头
    for row in reader:
        print(row)  # 列表

# 写入CSV
with open("output.csv", "w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["Name", "Age", "City"])
    writer.writerow(["Alice", 25, "Beijing"])
    writer.writerow(["Bob", 30, "Shanghai"])
```

### 5.2 DictReader/DictWriter

```python
import csv

# 读取为字典
with open("users.csv", "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        print(row["Name"], row["Age"])  # 按列名访问

# 字典写入
with open("output.csv", "w", encoding="utf-8", newline="") as f:
    fieldnames = ["Name", "Age", "City"]
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerow({"Name": "Alice", "Age": 25, "City": "Beijing"})
```

### 5.3 pandas处理CSV（推荐）

```python
import pandas as pd

# 读取CSV
df = pd.read_csv("users.csv")
print(df.head())
print(df["Name"])

# 筛选
adults = df[df["Age"] >= 18]

# 写入CSV
adults.to_csv("adults.csv", index=False)
```

---

## 📁 第六部分：二进制文件

### 6.1 图片/文件复制

```python
# 复制文件
with open("source.jpg", "rb") as src:
    with open("dest.jpg", "wb") as dst:
        dst.write(src.read())

# 或使用shutil
import shutil
shutil.copy("source.jpg", "dest.jpg")
```

### 6.2 结构化二进制（struct）

```python
import struct

# 打包数据
data = struct.pack("if", 42, 3.14)  # int + float

# 解包数据
num, value = struct.unpack("if", data)
print(num, value)  # 42 3.14
```

---

## 🎯 第七部分：面试速记

### Q1: with语句的作用？
**A**: 上下文管理器，确保文件等资源在使用后自动关闭。等价于Java的try-with-resources。

### Q2: JSON和pickle的区别？
**A**: JSON是文本格式，可读、安全、跨语言；pickle是二进制格式，支持更多Python类型但不安全。数据交换用JSON，Python内部缓存用pickle。

### Q3: pathlib和os.path的区别？
**A**: pathlib是面向对象的路径操作（Python 3.4+），更现代、更易用。os.path是旧式函数式API。推荐用pathlib。

### Q4: 如何处理大文件？
**A**: 逐行读取（`for line in f`），不要一次性读取全部内容。使用`with`语句确保文件关闭。

### Q5: encoding参数的作用？
**A`: 指定文件编码。中文文件常用`utf-8`，Windows系统默认可能是`gbk`。不指定编码可能导致乱码。

---

## 🔗 相关笔记

- [[14_Python数据结构详解]] — list/dict/tuple/set
- [[15_Python函数详解]] — 函数定义和参数
- [[19_Python基础语法速成]] — Python基础语法

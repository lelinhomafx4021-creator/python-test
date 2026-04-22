from collections import Counter
from random import choices
from string import ascii_letters, digits

# --- 知识点普及：工业级统计方式 ---

# 1. 模拟海量证券代码数据
print("正在生成 1000 个随机字符数据...")
z = "".join(choices(ascii_letters + digits, k=1000))

# 2. 传统做法：使用 dict().get()
# 优点：不依赖第三方库，逻辑清晰。
# 缺点：代码稍长。
def count_legacy(data):
    d = {}
    for ch in data:
        d[ch] = d.get(ch, 0) + 1
    return d

# 3. 专家级做法：使用 collections.Counter
# 优点：
#   - 极简：一行实现统计。
#   - 性能：底层 C 语言优化。
#   - 功能：自带 most_common() 等杀手级功能。
# 面试讲点：Pythonic 代码的典范。
print("正在使用 Counter 进行高效统计...")
counts = Counter(z)

# 4. 展示最具代表性的前 5 个“活跃字符”
print("\n[统计报告]")
print("-" * 30)
for char, count in counts.most_common(5):
    print(f"字符 '{char}' | 出现次数: {count}")
print("-" * 30)

# 如果想知道总共有多少种不同字符：
print(f"总计不同字符数: {len(counts)}")

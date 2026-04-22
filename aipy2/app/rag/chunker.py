"""
文本切片模块 - 把大段文本切成适合向量检索的小块

======== 面试核心知识点 ========
Q: 为什么要切片（Chunking）？
A: 三个原因：
   1. 嵌入模型有最大输入长度限制（一般 512 token），太长的文本截断就丢信息了
   2. 向量检索在"块"级别做匹配，块太大→噪声多、匹配不精准
   3. LLM 的上下文窗口有限，塞太多无关文本会降低回答质量

Q: chunk_size 怎么调？
A: 没有银弹，取决于：
   - 文档类型：技术文档信息密度高 → 小块（300-500）
   - 查询类型：问细节 → 小块；问综述 → 大块（800-1200）
   - 嵌入模型：大部分模型 512 token 最佳
   实践建议：先用 500 字符 + 100 overlap 跑通，再根据检索效果调

Q: 什么是 overlap（重叠）？
A: 切片时让相邻块有一部分重叠内容。
   比如第 1 块的末尾 100 字和第 2 块的开头 100 字是一样的。
   目的：防止关键信息被切在两个块的边界上，导致两边都匹配不到。
   代价：存储量增加，搜索所要检索的数据变多。

Q: RecursiveCharacterTextSplitter 的原理？（LangChain 面试常考）
A: 核心思想是"递归地尝试不同粒度的分隔符"：
   优先按 \n\n 切（段落边界，语义最完整）
   切完还太大 → 按 \n 切（行边界）
   还大 → 按句号切（句子边界）
   还大 → 按空格切（词边界）
   最后 → 逐字符切
   我们下面手写一个简化版，面试时说"我参考 LangChain 的递归思路实现了自己的切片器"
========================
"""

from app.rag.parser import DocChunk


def split_text(
    text: str,
    chunk_size: int = 500,
    overlap: int = 100,
) -> list[str]:
    """
    把一段长文本切成多个小块

    参数:
    - text: 原始长文本
    - chunk_size: 每个块的目标长度（字符数）
    - overlap: 相邻块的重叠字符数

    返回: 切片后的文本列表

    实现思路（简化版递归切片）：
    1. 按段落分隔符 \n\n 把文本拆成段落
    2. 把小段落合并到一起，直到接近 chunk_size
    3. 达到 chunk_size 后"断开"，开始新的块
    4. 新块的开头会加上前一个块的末尾（overlap）
    """

    # 如果文本本身就够短，不需要切，直接返回
    # 这是递归/循环的"终止条件"，别忘了写，不然可能死循环
    # ============================================================
    # 第一步：按段落拆开
    # ============================================================
    # 用 \n\n 切是因为大部分文档的段落之间都是空行分隔的
    # 这样切出来的每个 part 是一个完整段落，语义完整性最好
    parts = text.split("\n\n")

    # 如果整个文本没有 \n\n（比如一段话连着写），就降级用 \n 切
    if len(parts) == 1:
        parts = text.split("\n")

    # 如果连 \n 都没有（一整段没换行的文本），按句号切
    if len(parts) == 1:
        # 中文句号和英文句号都考虑
        # replace 先把中文句号统一成英文句号，然后 split
        parts = text.replace("。", "。\n").replace(". ", ".\n").split("\n")

    # ============================================================
    # 第二步：合并小段落 → 组装成 chunk
    # ============================================================
    chunks = []
    current_chunk = ""  # 正在组装的当前块

    for part in parts:
        part = part.strip()
        if not part:
            continue

        # 判断：把这个段落加进来后，会不会超过 chunk_size？
        if len(current_chunk) + len(part) + 1 <= chunk_size:
            # 不会超 → 加进来，用换行符连接
            # 这里的 +1 是换行符的长度
            if current_chunk:
                current_chunk += "\n" + part
            else:
                current_chunk = part
        else:
            # 会超 → 当前块已经"满"了，保存它，开始新块
            if current_chunk:
                chunks.append(current_chunk)
            current_chunk = part

    # 循环结束后，最后一个块可能还没保存
    if current_chunk.strip():
        chunks.append(current_chunk)

    # ============================================================
    # 第三步：处理超长段落
    # ============================================================
    # 有些段落本身就超过 chunk_size（比如一段话写了 2000 字不换行）
    # 这种情况需要强制切割
    final_chunks = []
    for chunk in chunks:
        if len(chunk) <= chunk_size:
            final_chunks.append(chunk)
        else:
            # 强制按 chunk_size 切割，保留 overlap
            start = 0
            while start < len(chunk):
                end = start + chunk_size
                final_chunks.append(chunk[start:end])
                # 下一块从 (end - overlap) 开始，这样就有重叠了
                start = end - overlap
                # 防止 overlap >= chunk_size 导致死循环
                if start >= len(chunk):
                    break

    # ============================================================
    # 第四步：添加块间 overlap
    # ============================================================
    # 对于正常切片的块（非强制切割的），也要添加 overlap
    if overlap > 0 and len(final_chunks) > 1:
        overlapped = [final_chunks[0]]  # 第一个块不需要加 overlap 前缀

        for i in range(1, len(final_chunks)):
            # 取前一个块的末尾 overlap 个字符
            prev_tail = final_chunks[i - 1][-overlap:]
            # 拼到当前块的开头
            overlapped.append(prev_tail + "\n" + final_chunks[i])

        final_chunks = overlapped

    return final_chunks


def chunk_documents(
    docs: list[DocChunk],
    chunk_size: int = 500,
    overlap: int = 100,
) -> list[DocChunk]:
    """
    对解析出的文档块做进一步切片

    为什么要单独一个函数，而不是在 parser 里直接切？
    → 职责分离（Single Responsibility Principle）
    → parser 只负责"把文件变成文本"
    → chunker 只负责"把文本切成小块"
    → 每个模块只做一件事，好测试、好替换
    面试说"我按 SRP 原则做了模块解耦"会加分

    关键设计：
    切片后的每个 DocChunk 会继承原始文档的元数据（source、page 等），
    并额外加上 chunk_index。
    这样检索到某个块时，能追溯到"这段话来自《XX研报》第3页的第2个切片"。
    RAG 的可溯源性（traceability）是企业级应用的刚需。
    """

    result = []

    for doc in docs:
        # 对每个文档块的文本做切片
        pieces = split_text(doc.text, chunk_size, overlap)

        for idx, piece in enumerate(pieces):
            # 构造新的 DocChunk，继承原始元数据 + 加上切片信息
            result.append(DocChunk(
                text=piece,
                metadata={
                    **doc.metadata,               # ** 解包：继承所有原始元数据
                    "chunk_index": idx,            # 第几个切片（从 0 开始）
                    "total_chunks": len(pieces),   # 这个文档被切成了几块
                }
            ))

    print(f"[切片] 输入 {len(docs)} 个文档块 → 输出 {len(result)} 个切片 "
          f"(chunk_size={chunk_size}, overlap={overlap})")
    return result

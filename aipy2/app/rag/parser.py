"""
文档解析模块 - 把 PDF / Word 原始文件变成纯文本

======== 面试知识点 ========
Q: 为什么不能直接把 PDF 文本丢给 LLM？
A: 1. PDF 可能有几百页，LLM 有上下文长度限制（比如 8K/128K token）
   2. 直接丢一坨文本，LLM 找不到重点，回答质量差
   3. 所以要先解析 → 切片 → 向量化 → 按需检索，这就是 RAG 的核心思想

Q: PDF 解析有什么坑？
A: PDF 不是"文档格式"，而是"页面描述语言"。
   文字是"画"在画布上的，没有段落/标题的语义结构。
   所以 PDF 解析永远不完美，不同库的策略不同：
   - PyMuPDF (fitz): C 引擎，速度快，按页提取
   - pdfplumber: 基于 pdfminer，擅长提取表格
   - Unstructured: 企业级，支持 OCR 识别扫描件
   我们用 PyMuPDF，兼顾速度和质量。

Q: Word 解析为什么比 PDF 简单？
A: .docx 本质是一个 ZIP 压缩包，里面是 XML 文件。
   每个段落都有明确的 XML 标签，python-docx 解析 XML 就能拿到文本。
   比 PDF "猜"文本结构靠谱得多。
========================
"""

from dataclasses import dataclass, field
from pathlib import Path

# pymupdf 安装的包名是 pymupdf，但 import 时叫 fitz
# 这是历史遗留问题，fitz 是底层 C 引擎 MuPDF 中一个模块的名字
import fitz

# python-docx 的 Document 和我们自己定义的 DocChunk 重名了
# 所以 import 时重命名为 WordDoc，避免混淆
from docx import Document as WordDoc


# ============================================================
# 数据结构定义
# ============================================================

@dataclass
class DocChunk:
    """
    文档块 - 整个 RAG 管道中流转的最小数据单元

    为什么用 dataclass 而不是 dict？
    - 有类型提示，IDE 能自动补全，不容易拼错 key
    - 面试时说"我用 dataclass 做数据建模"比"我用 dict"高级

    为什么不用 Pydantic BaseModel？
    - 这里不涉及接口校验，dataclass 更轻量
    - Pydantic 适合 API 层做请求/响应校验
    """
    text: str                                      # 文本内容
    metadata: dict = field(default_factory=dict)    # 元数据（来源文件、页码等）
    # field(default_factory=dict) 是 dataclass 处理可变默认值的方式
    # 如果直接写 metadata: dict = {} 会导致所有实例共享同一个 dict（经典 Python 坑）


# ============================================================
# PDF 解析
# ============================================================

def parse_pdf(file_path: str) -> list[DocChunk]:
    """
    解析 PDF 文件，每一页变成一个 DocChunk

    参数: file_path - PDF 文件的绝对路径或相对路径
    返回: DocChunk 列表，每个元素对应一页的文本
    """

    # fitz.open() 打开 PDF 文件
    # 底层是 C 代码在解析，速度是纯 Python 库（比如 PyPDF2）的 10-50 倍
    doc = fitz.open(file_path)

    # Path 是 pathlib 模块的路径对象，比 os.path 更现代
    # .name 属性取文件名（不含目录），比如 "研报.pdf"
    file_name = Path(file_path).name

    chunks = []

    # enumerate(doc, start=1) 遍历每一页
    # start=1 让页码从 1 开始（人类习惯），而不是程序员的 0
    for page_num, page in enumerate(doc, start=1):

        # get_text("text") 提取该页的纯文本
        # 其他选项：
        #   "html"  → 带格式的 HTML
        #   "dict"  → 详细的文本块信息（坐标、字体等）
        #   "blocks" → 文本块列表
        # 我们只需要纯文本，所以用 "text"
        text = page.get_text("text")

        # strip() 去掉首尾空白符（空格、换行、制表符等）
        # 有些 PDF 页面可能是空白页（比如封面背面），需要过滤掉
        text = text.strip()
        if not text:
            continue

        # 构造 DocChunk，带上元数据
        # 元数据非常重要！检索到这段文本后，用户想知道"这段话来自哪个文件的第几页"
        chunks.append(DocChunk(
            text=text,
            metadata={
                "source": file_name,           # 来源文件名
                "page": page_num,              # 第几页
                "total_pages": len(doc),        # 总页数
                "file_type": "pdf",            # 文件类型标记
            }
        ))

    # 关闭文件，释放 C 层的内存
    # 不关的话可能会内存泄漏（虽然 Python 有 GC，但 C 层资源不受 GC 管）
    doc.close()

    print(f"[PDF解析] {file_name}: 共 {len(doc)} 页，提取了 {len(chunks)} 个文本块")
    return chunks


# ============================================================
# Word 解析
# ============================================================

def parse_docx(file_path: str) -> list[DocChunk]:
    """
    解析 Word (.docx) 文件

    Word 和 PDF 的关键区别：
    - PDF 按"页"组织，每页独立
    - Word 按"段落"组织，页是渲染时才确定的
    所以 Word 解析出来的是"所有段落拼接"的一整段文本，没有页码概念
    """

    # WordDoc() 解析 .docx 文件
    # 底层：解压 ZIP → 读取 word/document.xml → 解析 XML 树
    doc = WordDoc(file_path)
    file_name = Path(file_path).name

    # 收集所有非空段落
    # doc.paragraphs 是一个列表，每个元素是一个 Paragraph 对象
    paragraphs = []
    for para in doc.paragraphs:
        # para.text 拿到这个段落的纯文本
        text = para.text.strip()
        if text:
            paragraphs.append(text)

    # 所有段落用换行符拼接成一整段
    # 为什么不直接每个段落一个 DocChunk？
    # 因为有些段落只有一两个字（比如标题），太碎了
    # 切片的工作交给后面的 chunker 来做，这里只负责"解析"
    if not paragraphs:
        print(f"[Word解析] {file_name}: 没有提取到文本")
        return []

    full_text = "\n".join(paragraphs)

    chunks = [DocChunk(
        text=full_text,
        metadata={
            "source": file_name,
            "file_type": "docx",
            "paragraphs": len(paragraphs),     # 段落数量
        }
    )]

    print(f"[Word解析] {file_name}: 提取了 {len(paragraphs)} 个段落，"
          f"共 {len(full_text)} 字符")
    return chunks


# ============================================================
# 统一入口 - 自动识别文件类型
# ============================================================

def parse_file(file_path: str) -> list[DocChunk]:
    """
    根据文件后缀名自动选择解析器

    设计思想：
    外部调用者不需要关心文件类型，扔个路径进来就行
    这是"策略模式"的简化版 —— 根据条件选择不同的处理策略
    面试说"我用了策略模式来解耦文件类型和解析逻辑"会加分
    """

    # Path(file_path).suffix 拿到文件后缀，比如 ".pdf"
    # .lower() 统一转小写，防止 ".PDF" 匹配不上
    ext = Path(file_path).suffix.lower()

    # 根据后缀分发到对应的解析函数
    if ext == ".pdf":
        return parse_pdf(file_path)

    elif ext == ".docx":
        return parse_docx(file_path)

    elif ext in {".txt", ".md"}:
        with open(file_path, "r", encoding="utf-8") as f:
            text = f.read()
        return [DocChunk(text=text, metadata={"source": Path(file_path).name, "file_type": ext[1:]})]

    elif ext == ".doc":
        # .doc 是旧版 Word 格式（二进制），python-docx 不支持
        # 需要用 LibreOffice 或者 antiword 转换
        raise ValueError(
            f"不支持 .doc 格式（旧版 Word），请用 Word 另存为 .docx 格式"
        )

    else:
        raise ValueError(
            f"不支持的文件类型: {ext}，目前支持 .pdf 和 .docx"
        )


# ============================================================
# 批量解析目录下的所有文件
# ============================================================

def parse_dir(dir_path: str) -> list[DocChunk]:
    """
    扫描目录下所有 PDF/Word 文件并解析

    用 pathlib 的 glob 模式匹配文件：
    - "*.pdf" 匹配当前目录的 PDF
    - "**/*.pdf" 会递归搜索子目录（我们这里不用递归，避免误扫描）
    """

    dir_p = Path(dir_path)

    # 检查目录是否存在
    if not dir_p.exists():
        raise FileNotFoundError(f"目录不存在: {dir_path}")
    if not dir_p.is_dir():
        raise ValueError(f"不是目录: {dir_path}")

    # 收集所有支持的文件
    # iterdir() 遍历目录下的所有文件和子目录
    supported = {".pdf", ".docx", ".txt", ".md"}
    files = [
        f for f in dir_p.iterdir()
        if f.is_file() and f.suffix.lower() in supported
    ]

    if not files:
        print(f"[警告] 目录 {dir_path} 下没有找到 PDF/DOCX 文件")
        return []

    print(f"[扫描] 在 {dir_path} 下找到 {len(files)} 个文件:")
    for f in files:
        print(f"  - {f.name}")

    # 逐个解析，合并结果
    all_chunks = []
    for file in files:
        try:
            chunks = parse_file(str(file))
            all_chunks.extend(chunks)
        except Exception as e:
            # 单个文件解析失败不应该中断整个流程
            # 这是"优雅降级"的思想：能处理的处理，不能的跳过并记录
            print(f"[错误] 解析 {file.name} 失败: {e}")
            continue

    print(f"[汇总] 共解析出 {len(all_chunks)} 个文档块")
    return all_chunks

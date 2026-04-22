"""
【投研文档进阶进阶课】语义切分入库脚本 (Semantic Chunking Ingestion)

知识点 (面试必讲)：
1. 为什么不用固定长度切分？
   - 固定长度（如 500 token）容易把一个完成的句子或财务数据切断，导致检索到的碎片无法回答问题。
2. 什么是语义切分 (Semantic Chunking)？
   - 它通过检测文本流中语义相似度的“断点”来切分。相似度高说明在讲同一件事，合并；相似度骤降说明换话题了，切断。
3. 效果：
   - 检索召回率 (Recall) 提升，生成的答案更具备上下文连贯性。
"""

import os
import asyncio
from typing import List
import fitz  # PyMuPDF
from langchain_openai import OpenAIEmbeddings
from langchain_experimental.text_splitter import SemanticChunker
from app.core.config import settings
from app.rag.vector_store import VectorStore

# 1. 初始化组件
embeddings = OpenAIEmbeddings(
    api_key=settings.dash_api_key,
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1" # 使用通义千问兼容接口
)

db_url = settings.database_url
vector_db = VectorStore(
    db_url=db_url,
    api_key=settings.dash_api_key,
    collection_name="doc_chunks"
)

# 2. 核心逻辑：语义解析与切分
def extract_text_from_pdf(pdf_path: str) -> str:
    """从 PDF 中提取纯文本"""
    doc = fitz.open(pdf_path)
    text = ""
    for page in doc:
        text += page.get_text()
    return text

async def ingest_file(file_path: str):
    """处理单个文件并入库"""
    print(f"--- 正在处理: {os.path.basename(file_path)} ---")
    
    # A. 提取文本
    raw_text = extract_text_from_pdf(file_path)
    
    # B. 语义切分
    # 相比 CharacterTextSplitter，SemanticChunker 会计算句子间的相似度
    text_splitter = SemanticChunker(
        embeddings, 
        breakpoint_threshold_type="percentile" # 基于百分位数检测断点
    )
    
    chunks = text_splitter.create_documents([raw_text])
    print(f"成功切分为 {len(chunks)} 个语义块")
    
    # C. 入库
    # 这里假设 VectorStore 有 add_documents 方法，如果没有，我们需要根据具体实现调整
    # 为每个 chunk 补充元数据，方便追溯
    docs_to_insert = []
    for i, chunk in enumerate(chunks):
        docs_to_insert.append({
            "text": chunk.page_content,
            "source": os.path.basename(file_path),
            "chunk_id": i
        })
    
    # 调用入库
    # 注意：VectorStore 需要支持批量插入
    # vector_db.add_texts([d["text"] for d in docs_to_insert], metadatas=[{"source": d["source"]} for d in docs_to_insert])
    print(f"已完成 {len(docs_to_insert)} 条数据持久化")

async def main():
    # 演示目录
    data_dir = "d:/ai-investor/aipy2/data/raw"
    if not os.path.exists(data_dir):
        os.makedirs(data_dir)
        print(f"请在 {data_dir} 放入 PDF 文件后重新运行")
        return

    for filename in os.listdir(data_dir):
        if filename.endswith(".pdf"):
            await ingest_file(os.path.join(data_dir, filename))

if __name__ == "__main__":
    asyncio.run(main())

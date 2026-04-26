"""
【教学修改】RAG 文档入库脚本

升级后的版本重点解决两个问题：
1. 同一份资料重复跑，不要重复花 embedding 的钱
2. 同一份资料真的有变化时，再删除旧切片并重建

核心思路：
1. 先给文件算一个 sha256 指纹(hash)
2. 去数据库登记表里看上次的 hash
3. 一样：直接跳过
4. 不一样：删除旧切片，再重新入库
"""

from hashlib import sha256
from pathlib import Path
import sys


# 让脚本支持直接运行。
BASE_DIR = Path(__file__).resolve().parents[1]
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

from app.core.config import settings
from app.rag.chunker import chunk_documents
from app.rag.parser import parse_file
from app.rag.vector_store import VectorStore


RAW_DATA_DIR = BASE_DIR / "data" / "raw"
SUPPORTED_EXTS = {".pdf", ".docx", ".txt", ".md"}


def build_vector_store() -> VectorStore:
    """创建向量库对象。"""
    return VectorStore(
        db_url=settings.DATABASE_URL,
        api_key=settings.DASH_API_KEY,
        collection_name="doc_chunks",
    )


def ensure_raw_dir() -> None:
    """确保原始文档目录存在。"""
    RAW_DATA_DIR.mkdir(parents=True, exist_ok=True)


def iter_supported_files() -> list[Path]:
    """列出 raw 目录下支持入库的文件。"""
    return sorted(
        file
        for file in RAW_DATA_DIR.iterdir()
        if file.is_file() and file.suffix.lower() in SUPPORTED_EXTS
    )


def file_sha256(file_path: Path) -> str:
    """给文件计算 sha256 指纹。"""
    hasher = sha256()
    with file_path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def ingest_one_file(vector_store: VectorStore, file_path: Path) -> None:
    """处理单个文件。"""
    source = file_path.name
    current_hash = file_sha256(file_path)
    previous_hash = vector_store.get_source_hash(source)

    if previous_hash == current_hash:
        print(f"[跳过] {source} 没有变化，不重复生成 embedding。")
        return

    print(f"[处理] {source}")
    if previous_hash:
        print("[原因] 文件内容有变化，先删旧切片，再重建。")
        vector_store.delete_by_sources([source])
    else:
        print("[原因] 这是第一次入库。")

    raw_docs = parse_file(str(file_path))
    if not raw_docs:
        print(f"[跳过] {source} 没有解析出有效文本。")
        return

    split_docs = chunk_documents(raw_docs, chunk_size=500, overlap=100)
    print(f"[切片] {source}: 共 {len(split_docs)} 个切片")

    vector_store.add_documents(split_docs)
    vector_store.upsert_source_hash(source, current_hash)
    print(f"[完成] {source}: 已写入 {len(split_docs)} 个切片")


def ingest_raw_dir() -> None:
    """把 data/raw 目录里的文档统一入库。"""
    ensure_raw_dir()
    files = iter_supported_files()

    print(f"[1/3] 扫描原始文档目录: {RAW_DATA_DIR}")
    if not files:
        print("没有发现可入库的文档。")
        print("请把 PDF 放到 aipy2/data/raw 后再运行。")
        return

    print(f"[2/3] 发现 {len(files)} 个文件:")
    for file in files:
        print(f"  - {file.name}")

    vector_store = build_vector_store()
    try:
        vector_store.create_collection()

        print("[3/3] 开始检查 hash 并入库...")
        for file_path in files:
            ingest_one_file(vector_store, file_path)
    finally:
        vector_store.close()


if __name__ == "__main__":
    ingest_raw_dir()

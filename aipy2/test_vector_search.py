import os
from dotenv import load_dotenv
from app.rag.vector_store import VectorStore
from app.core.config import settings

load_dotenv()

# 初始化向量库
my_vector = VectorStore(
    db_url=settings.DATABASE_URL,
    api_key=settings.DASH_API_KEY,
    collection_name="doc_chunks",
)

try:
    print("Testing Vector Search...")
    # 尝试搜索一个关键词
    results = my_vector.search("茅台", top_k=3)
    print(f"Found {len(results)} results:")
    for res in results:
        print(f"- [{res['score']:.4f}] {res['source']}: {res['text'][:50]}...")
except Exception as e:
    print(f"Failed: {e}")
finally:
    my_vector.close()

"""本地财经热点采集脚本。

用法：
    D:\ai-investor\aipy2\.venv\Scripts\python.exe scripts\fetch_hot_news.py
"""

import json
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from app.tools.news_tool import collect_hot_news


if __name__ == "__main__":
    data = collect_hot_news(limit=12)
    print(json.dumps(data, ensure_ascii=False, indent=2))

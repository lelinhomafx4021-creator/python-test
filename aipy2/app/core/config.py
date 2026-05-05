"""
项目配置。
统一通过 pydantic-settings 从 `aipy2/.env` 读取运行参数，
避免在代码里散落硬编码配置。
"""

import os
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

"""
配置管理说明：
- 使用 pydantic-settings 自动从 .env 文件读取配置
- 所有配置项都会自动从环境变量或 .env 文件加载
- 大小写不敏感（case_sensitive=False）
"""


# 项目根目录：从当前文件向上三级（app/core/config.py -> app/core -> app -> 项目根目录）
ROOT_DIR = Path(__file__).resolve().parent.parent.parent
# .env 文件路径：所有敏感配置（API密钥、数据库连接等）都从这里读取
ENV_FILE = os.path.join(ROOT_DIR, ".env")


class Settings(BaseSettings):
    """应用配置对象。"""
    
    # 项目名称
    PROJECT_NAME: str = "AI-Investor-Core"
    # 运行环境：dev/production/test
    APP_ENV: str = "dev"

    # PostgreSQL 数据库连接 URL（格式：postgresql://user:password@host:port/dbname）
    DATABASE_URL: str

    # 阿里云 DashScope API 密钥（用于 Embedding 向量化）
    DASH_API_KEY: str
    # DeepSeek API 密钥（用于大模型推理）
    DEEPSEEK_API: str

    # Tavily 搜索 API 密钥（用于联网检索）
    SEARCHER_API: str = ""
    # Tavily API 地址
    SEARCHER_API_URL: str = "https://api.tavily.com"

    # Langfuse 可观测性追踪配置
    LANGFUSE_PUBLIC_KEY: str = ""
    LANGFUSE_SECRET_KEY: str = ""
    LANGFUSE_HOST: str = "http://localhost:3000"
    # 是否启用 Langfuse 追踪
    LANGFUSE_ENABLED: bool = True

    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    @property
    def is_dev(self) -> bool:
        return self.APP_ENV == "dev"


settings = Settings()

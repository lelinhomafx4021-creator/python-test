"""
项目配置。
统一通过 pydantic-settings 从 `aipy2/.env` 读取运行参数，
避免在代码里散落硬编码配置。
"""

import os
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


ROOT_DIR = Path(__file__).resolve().parent.parent.parent
ENV_FILE = os.path.join(ROOT_DIR, ".env")


class Settings(BaseSettings):
    """应用配置对象。"""

    PROJECT_NAME: str = "AI-Investor-Core"
    APP_ENV: str = "dev"

    # 本项目根目录 Docker Compose 默认把 PostgreSQL 暴露在 5432 端口。
    DATABASE_URL: str

    DASH_API_KEY: str
    DEEPSEEK_API: str

    SEARCHER_API: str = ""
    SEARCHER_API_URL: str = "https://api.tavily.com"

    LANGFUSE_PUBLIC_KEY: str = ""
    LANGFUSE_SECRET_KEY: str = ""
    LANGFUSE_HOST: str = "http://localhost:3000"
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

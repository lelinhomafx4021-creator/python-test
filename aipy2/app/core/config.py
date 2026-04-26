"""
项目配置 - 用 pydantic-settings 统一管理环境变量

知识点：
pydantic-settings 从 .env 文件读取配置，自动做类型转换和校验。
比手写 os.getenv() 强在：
1. 有类型提示，IDE 能补全
2. 启动时就校验，缺了变量直接报错（不会运行到一半才崩）
3. 支持默认值和多环境（dev/test/prod）
"""
import os
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

# 获取项目根目录（aipy2 这一层）
ROOT_DIR = Path(__file__).resolve().parent.parent.parent
ENV_FILE = os.path.join(ROOT_DIR, ".env")


class Settings(BaseSettings):
    """应用配置对象：统一封装环境变量与默认值。"""

    # --- 基础配置 ---
    PROJECT_NAME: str = "AI-Investor-Core"
    APP_ENV: str = "dev"  # 运行环境：开发/测试/生产
    # --- 数据库配置 ---
    DATABASE_URL: str  # PostgreSQL/MySQL 连接地址 (注意: 如果本地有冲突，请确保使用 5433)
    # --- AI 基座配置 ---
    DASH_API_KEY: str
    DEEPSEEK_API: str
    # --- 修正：对应 .env 里的 SEARCHER_API ---
    SEARCHER_API: str = "" 
    SEARCHER_API_URL: str = "https://api.tavily.com"
    # --- LangFuse 观测配置 ---
    LANGFUSE_PUBLIC_KEY: str = ""
    LANGFUSE_SECRET_KEY: str = ""
    LANGFUSE_HOST: str = "http://localhost:3000"
    LANGFUSE_ENABLED: bool = True
    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False # 忽略大小写
    )

    @property
    def is_dev(self) -> bool:
        """判断当前是否处于开发环境。"""
        return self.APP_ENV == "dev"

# 全局单例
settings = Settings()

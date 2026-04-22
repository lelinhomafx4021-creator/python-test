"""
企业级日志配置。
知识点：在 Linux 环境下，通常输出为 JSON 格式以便阿里云/ELK 采集。
本地开发则采用彩色格式。
"""
# d:\ai-investor\aipy2\app\core\logger.py
import logging
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path
from app.core.config import settings


def setup_logger() -> logging.Logger:
    # 1️⃣ 创建（或获取）同名 logger，整个进程共享
    logger = logging.getLogger("aipy2")
    logger.setLevel(logging.INFO if not settings.is_dev else logging.DEBUG)

    # 2️⃣ 统一的日志格式
    formatter = logging.Formatter(
        "[%(asctime)s] [%(levelname)s] [%(name)s] - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # 3️⃣ 控制台输出（开发时最常用）
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)

    # 4️⃣ 文件输出（写到项目根目录）
    #    - 文件名 aipy2.log
    #    - 每 10 MB 自动切分，保留最近 5 份
    log_path = Path(__file__).resolve().parents[2] / "aipy2.log"   # 项目根目录
    file_handler = RotatingFileHandler(
        filename=log_path,
        maxBytes=10 * 1024 * 1024,   # 10 MB
        backupCount=20,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)

    # 5️⃣ 防止重复添加 handler（关键！）
    if not logger.handlers:
        logger.addHandler(console_handler)   # 控制台
        logger.addHandler(file_handler)      # 文件

    return logger


# 模块加载时直接实例化，供全局使用
logger = setup_logger()

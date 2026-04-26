"""应用日志初始化模块。"""

import logging
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path

from app.core.config import settings


class SafeConsoleStream:
    """控制台安全输出包装器，避免编码不一致导致日志写入失败。"""

    def __init__(self, stream):
        """保存原始输出流并记录其编码信息。"""
        self._stream = stream
        self.encoding = getattr(stream, "encoding", None) or "utf-8"

    def write(self, text):
        """将无法编码的字符转义后再写入控制台。"""
        safe_text = text.encode(self.encoding, errors="backslashreplace").decode(
            self.encoding,
            errors="ignore",
        )
        return self._stream.write(safe_text)

    def flush(self):
        """透传 flush，保证日志及时刷到终端/文件。"""
        return self._stream.flush()


def setup_logger() -> logging.Logger:
    """创建并返回全局日志器（控制台 + 轮转文件）。"""
    logger = logging.getLogger("aipy2")
    logger.setLevel(logging.INFO if not settings.is_dev else logging.DEBUG)

    # 统一日志格式：时间 + 级别 + 模块名 + 消息
    formatter = logging.Formatter(
        "[%(asctime)s] [%(levelname)s] [%(name)s] - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    console_handler = logging.StreamHandler(SafeConsoleStream(sys.stdout))
    console_handler.setFormatter(formatter)

    log_path = Path(__file__).resolve().parents[2] / "aipy2.log"
    file_handler = RotatingFileHandler(
        filename=log_path,
        maxBytes=10 * 1024 * 1024,
        backupCount=20,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)

    if not logger.handlers:
        logger.addHandler(console_handler)
        logger.addHandler(file_handler)

    return logger


logger = setup_logger()

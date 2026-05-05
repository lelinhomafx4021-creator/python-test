"""数据库连接与会话工具。

这个文件的职责很简单：
1. 创建全局数据库引擎 `engine`
2. 提供本地测试用的 `create_all` 辅助函数
3. 提供 FastAPI 路由里常用的数据库会话 `get_session`

注意：
- 正式环境的表结构管理，已经统一交给 Alembic
- 这里的 `init_db()` / `init_tables()` 只建议本地实验或测试时使用
"""

# `Session`：数据库会话对象，用来执行增删改查
# `SQLModel`：模型元数据入口
# `create_engine`：创建数据库连接引擎
from sqlmodel import Session, SQLModel, create_engine

# 导入项目配置，读取 DATABASE_URL。
from app.core.config import settings

# 创建数据库引擎。
# 这是项目里所有数据库操作共享的底层连接入口。
engine = create_engine(
    # 数据库连接地址，例如 PostgreSQL 的连接串。
    settings.DATABASE_URL,
    # 开发环境下可以打印 SQL，方便新手观察实际执行了什么语句。
    echo=settings.is_dev,
    # 在真正使用连接前先做一次“心跳检查”，避免拿到失效连接。
    pool_pre_ping=True,
)


def init_db():
    """本地/测试辅助函数：根据 ORM 模型补齐缺失表。"""
    # 这些 import 的作用不是在这里直接使用变量，
    # 而是把表模型加载进来，让 SQLModel.metadata 能看到它们。
    # 只有 Python 端实际使用的模型才需要在这里注册。
    import app.models.agent_run_audit  # noqa: F401

    # `create_all` 适合本地快速补表，但它不是正式的迁移工具。
    SQLModel.metadata.create_all(engine)


def init_tables(*tables):
    """本地/测试辅助函数：只初始化指定表。"""
    SQLModel.metadata.create_all(engine, tables=list(tables))


def get_session():
    """FastAPI 依赖函数：按请求提供一个短生命周期数据库会话。"""
    with Session(engine) as session:
        yield session

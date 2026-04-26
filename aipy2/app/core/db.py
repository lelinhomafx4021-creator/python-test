"""
 【数据心脏：app/core/db.py】
 -----------------------------------------------------------
 职责：掌管所有与 PostgreSQL 数据库的通讯链路。
 
 面试讲点：
 1. 为什么用 Engine？Engine 是连接池的管理者，它负责跟数据库握手、保持长连。
 2. 为什么用 Session？Session 是单笔业务的“草稿纸”，在一连串操作结束后统一提交（Commit）。
"""

from sqlmodel import create_engine, SQLModel, Session
from app.core.config import settings

# 建立连接引擎
# echo=True：每当你操作一次数据库，终端都会打印出对应的原生 SQL 语句，强烈建议新手仔细观察！
engine = create_engine(
    settings.DATABASE_URL, 
    echo=settings.is_dev,
    pool_pre_ping=True # “起搏器”逻辑：如果数据库连接由于超时挂了，它会自动重连
)

def init_db():
    """
    【一键建表】
    逻辑：扫描整个 app 里的模型定义，把还没在数据库里露脸的表全部建好。
    """
    # 重要：必须在此导入你的模型类文件，SQLModel 才能找到表定义
    import app.models.user_profile 
    import app.models.chat_turn
    import app.models.stock
    # 这行会根据“已导入模型”自动生成或对齐数据表。
    # 注意：它不会删除旧字段，偏“安全创建”；复杂迁移仍建议 Alembic。
    SQLModel.metadata.create_all(engine)

def init_tables(*tables):
    """按需初始化指定表，避免一次性创建全部模型表。"""
    SQLModel.metadata.create_all(engine, tables=list(tables))

def get_session():
    """
    【会话生成器】
    这是 FastAPI 依赖注入 (Depends) 的标配写法。
    使用了 yield 关键字：请求进来时 yield session，请求结束自动执行后续的清理逻辑。
    """
    # 这是一个“生成器依赖”：
    # - 进入函数时创建 session
    # - 请求结束后（无论成功失败）自动关闭 session
    with Session(engine) as session:
        yield session

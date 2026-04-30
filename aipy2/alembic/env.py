"""Alembic 迁移环境配置。

这个文件不是业务代码，而是 Alembic 每次执行迁移前都会先加载的“准备脚本”。

你可以把它理解成 4 件事：
1. 找到项目代码在哪里
2. 找到数据库要连哪一个
3. 告诉 Alembic：项目里有哪些 ORM 表定义
4. 真正建立数据库连接并执行迁移
"""

# `os` 用来拿当前工作目录。
import os
# `sys` 用来修改 Python 的模块搜索路径。
import sys
# `fileConfig` 用来加载 alembic.ini 里的日志配置。
from logging.config import fileConfig

# Alembic 运行时上下文对象，迁移的核心入口。
from alembic import context
# SQLAlchemy 的引擎创建函数和连接池配置。
from sqlalchemy import engine_from_config, pool
# SQLModel 的总元数据对象，所有 `table=True` 的表都会注册到这里。
from sqlmodel import SQLModel

# 把当前项目根目录加入 Python 搜索路径。
# 这样 Alembic 才能成功导入 `app.core.config`、`app.models.*` 这些项目模块。
sys.path.append(os.getcwd())

# 导入项目配置对象，后面用它拿真实数据库连接地址。
from app.core.config import settings

# 下面这几个 import 看起来像“没用到”，其实是必须的。
# 作用不是在当前文件里调用它们，而是“触发模块加载”。
# 模块一加载，其中的 `SQLModel(table=True)` 类就会把表定义注册到 `SQLModel.metadata`。
import app.models.agent_run_audit  # noqa: F401
import app.models.chat_turn  # noqa: F401
import app.models.user_profile  # noqa: F401

# 取到 Alembic 当前运行时的配置对象。
config = context.config

# 用项目里的真实数据库地址，覆盖 alembic.ini 里那条占位假地址。
config.set_main_option("sqlalchemy.url", settings.DATABASE_URL)

# 如果存在 alembic.ini，就顺便把里面的日志配置也加载进来。
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# 告诉 Alembic：我们项目的 ORM 表定义总入口在这里。
target_metadata = SQLModel.metadata


def run_migrations_offline() -> None:
    """离线模式：不连接数据库，只生成 SQL 文本。

    你们项目平时几乎不会用到这个模式，
    但 Alembic 官方模板一般都会保留它，兼容导出 SQL 的场景。
    """
    context.configure(
        # 离线模式只需要数据库 URL，不需要真实连接。
        url=config.get_main_option("sqlalchemy.url"),
        # 告诉 Alembic 参考哪份元数据。
        target_metadata=target_metadata,
        # 把绑定参数直接展开成字面量。
        literal_binds=True,
        # SQL 参数格式使用 named 风格。
        dialect_opts={"paramstyle": "named"},
    )

    # 开启迁移事务上下文。
    with context.begin_transaction():
        # 执行迁移逻辑。
        context.run_migrations()


def run_migrations_online() -> None:
    """在线模式：连接数据库并直接执行迁移。

    这是你们项目平时真正会走的主路径，
    例如执行 `uv run alembic upgrade head` 时，基本就是走这里。
    """
    connectable = engine_from_config(
        # 从 alembic.ini 当前 section 读取配置。
        config.get_section(config.config_ini_section, {}),
        # 只挑出 `sqlalchemy.` 前缀的配置项。
        prefix="sqlalchemy.",
        # 迁移场景通常不需要连接池复用，NullPool 更简单。
        poolclass=pool.NullPool,
    )

    # 真正打开一个数据库连接。
    with connectable.connect() as connection:
        context.configure(
            # 把这个真实连接交给 Alembic。
            connection=connection,
            # 同时告诉 Alembic 参考哪份 ORM 元数据。
            target_metadata=target_metadata,
        )

        # 开启迁移事务。
        with context.begin_transaction():
            # 真正执行 upgrade / downgrade。
            context.run_migrations()


# 根据当前运行模式，决定走离线还是在线。
# 你们平时基本都会走 else 这一支。
if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()

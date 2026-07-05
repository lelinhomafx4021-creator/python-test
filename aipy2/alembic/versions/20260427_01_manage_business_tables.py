"""初始化项目业务表结构。

当前项目约定：
1. 这份迁移文件统一管理“项目自己负责的业务表”
2. LangGraph 自己的 checkpoint 表不放这里，由框架的 setup() 自管
3. 正式初始化数据库结构时，执行 `uv run alembic upgrade head`
4. 版本元信息保留在 Alembic Python 文件里，具体 SQL 放外部 `.sql` 文件
"""

from pathlib import Path

from alembic import op

# 当前迁移文件自己的版本号。
revision = "20260427_01"
# 没有前置版本，表示这是当前整理后的“初始迁移”。
down_revision = None
# 下面两个一般保持默认即可。
branch_labels = None
depends_on = None


def _run_sql_file(filename: str) -> None:
    """执行同版本对应的外部 SQL 文件。

    这里使用自定义分隔符 `--;;` 拆分多条 SQL，避免把所有 DDL 塞进 Python。
    """
    sql_dir = Path(__file__).resolve().parents[1] / "sql"
    sql_text = (sql_dir / filename).read_text(encoding="utf-8")
    statements = [chunk.strip() for chunk in sql_text.split("\n--;;\n") if chunk.strip()]
    for statement in statements:
        op.execute(statement)


def upgrade() -> None:
    """升级迁移：把数据库结构升到当前版本。"""
    _run_sql_file("20260427_01_up.sql")


def downgrade() -> None:
    """回滚迁移：把当前版本创建的表和索引删掉。"""
    _run_sql_file("20260427_01_down.sql")

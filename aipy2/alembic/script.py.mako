"""${message}

Revision ID: ${up_revision}
Revises: ${down_revision | comma,n}
Create Date: ${create_date}

"""
from pathlib import Path
from typing import Sequence, Union

from alembic import op
${imports if imports else ""}

# revision identifiers, used by Alembic.
revision: str = ${repr(up_revision)}
down_revision: Union[str, None] = ${repr(down_revision)}
branch_labels: Union[str, Sequence[str], None] = ${repr(branch_labels)}
depends_on: Union[str, Sequence[str], None] = ${repr(depends_on)}


def _run_sql_file(filename: str) -> None:
    """Execute external SQL statements split by `--;;` separators."""
    sql_dir = Path(__file__).resolve().parents[1] / "sql"
    sql_text = (sql_dir / filename).read_text(encoding="utf-8")
    statements = [chunk.strip() for chunk in sql_text.split("\n--;;\n") if chunk.strip()]
    for statement in statements:
        op.execute(statement)


def upgrade() -> None:
    _run_sql_file("${up_revision}_up.sql")


def downgrade() -> None:
    _run_sql_file("${up_revision}_down.sql")

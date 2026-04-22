"""数据库连接（统一 SQLModel + asyncpg）。"""

from sqlmodel.ext.asyncio.session import AsyncSession
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.core.config import settings


async_url = settings.database_url.replace(
    "postgresql://",
    "postgresql+asyncpg://",
)

engine = create_async_engine(
    async_url,
    echo=False,
    pool_size=5,
    max_overflow=10,
)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False,
    autocommit=False,
)


async def get_session():
    async with AsyncSessionLocal() as session:
        yield session

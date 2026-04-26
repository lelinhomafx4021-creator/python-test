"""股票领域数据模型定义。"""

from pydantic import BaseModel, Field
from sqlmodel import SQLModel, Field as SField


class StockDTO(BaseModel):
    """股票数据传输对象。"""

    # 展示/传输层字段
    code: str = Field(..., description="股票代码")
    name: str = Field(..., description="股票名称")
    price: float = Field(..., description="当前股价")
    change_percent: float = Field(..., description="涨跌幅(%)")
    volume: float | None = Field(None, description="成交量")
    update_time: str = Field(..., description="行情更新时间")

    @classmethod
    def from_api(cls, raw_data: dict):
        """将行情接口原始字段转换为标准 DTO。"""
        return cls(
            code=raw_data.get("symbol"),
            name=raw_data.get("name"),
            price=float(raw_data.get("trade", 0)),
            change_percent=float(raw_data.get("changepercent", 0)),
            update_time=raw_data.get("ticktime", ""),
        )


class Stock(SQLModel, table=True):
    """股票信息持久化模型。"""

    __table_args__ = {"comment": "大A的股票信息表"}
    # 数据库主键
    id: int | None = SField(
        description="数据库自动生成",
        primary_key=True,
        sa_column_args={"comment": "股票的主键"},
    )
    # 业务字段
    stock_name: str = SField(description="股票名称", sa_column_args={"comment": "股票的名称"})
    stock_code: str = SField(description="股票代码", sa_column_args={"comment": "股票的代码"})

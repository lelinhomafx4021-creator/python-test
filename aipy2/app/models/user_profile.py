"""
 【用户画像模型：user_profile.py】
 -----------------------------------------------------------
 职责：描述数据库中的 user_profiles 表长什么样。
 
 核心知识：
 1. SQLModel = Pydantic (数据校验) + SQLAlchemy (数据库操作)。
 2. 这里的类既是“表的定义”，也是“数据的模子”。
"""

from datetime import datetime
from typing import Optional

from sqlmodel import SQLModel, Field

class UserProfile(SQLModel, table=True):
    """
    用户偏好画像表
    table=True: 这个配置告诉 SQLModel，这个 Python 类要对应数据库的一张表。
    """
    __tablename__ = "user_profiles"

    # Field(primary_key=True) 意味着这是这张表的主键（唯一身份证号）
    user_id: str = Field(primary_key=True, description="关联 Java 端的分布式唯一用户编号")
    
    # 风险偏好等级（低/中/高）
    # 虽然目前没用到，但它是 AI 投研的核心：AI 应该根据用户的风险承受力给出不同的分析。
    risk_level: str = Field(default="mid", description="风险偏好等级")
    
    # Optional 表示这个字段可以暂时不填，对应数据库空值
    interested_sectors: Optional[str] = Field(default=None, description="感兴趣的行业板块，如：半导体, 医疗")
    
    # default_factory 让系统在新建数据时自动填入当前时间，不需要手动传参。
    updated_at: datetime = Field(default_factory=datetime.now, description="最后更新时间")

from pydantic import BaseModel, Field
from typing import List

class StructuredInvestmentAnalysis(BaseModel):
    """
    【结构化投研报告模型】
    由 Pydantic 定义，AI 必须严格遵守此格式。
    """
    conclusion: str = Field(description="核心投研结论，50字以内")
    analysis_points: List[str] = Field(description="详细分析要点，至少列出3点核心逻辑")
    risk_level: str = Field(description="风险评级，只能在[低风险, 中等风险, 高风险]中选择")
    risk_tips: List[str] = Field(description="核心风险警示，列出具体的潜在亏损点")
    tags: List[str] = Field(description="行业或题材标签，如：半导体, 蓝筹股")

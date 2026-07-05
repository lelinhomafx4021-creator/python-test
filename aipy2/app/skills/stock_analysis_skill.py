"""股票分析技能：封装检索、行情获取与证据整理逻辑。"""

import json
import logging
from typing import Any

from pydantic import BaseModel, Field

from app.tools.common import extract_stock_code
from app.tools.retriever_tool import run_retrieval_async
from app.tools.stockdata_tool import get_stock_quote_core

# 【类比 Java】：这相当于在类里声明一个静态日志器。
logger = logging.getLogger(__name__)


# --- 1. 输入模型 ---
class StockAnalysisSkillInput(BaseModel):
    """
    【智能体的指令集】 -> 【类比 Java】：这就相当于 @RequestBody 接收的 DTO 类
    Pydantic 提供了极强的字段校验能力，类似 Hibernate Validator (@NotNull, @Min)
    """
    # 聊天场景里用户经常会输入较长的上下文，不能在进入检索前就把问题挡掉。
    query: str = Field(..., min_length=1, max_length=2000, description="用户最初输入的那个问题")
    queries: list[str] = Field(default_factory=list, description="Agent 拆解出来的多个精确搜索词")
    top_k: int = Field(default=3, ge=1, le=8, description="召回条数，类似 @Min(1) @Max(8)")

    # 新增的字段，展示如何控制业务逻辑：
    require_quote: bool = Field(default=True, description="如果为 True，Agent 会强制去查实时行情")


# --- 2. 输出模型 ---
class StockAnalysisSkillOutput(BaseModel):
    """
    【智能体的成果包】 -> 【类比 Java】：VO 类 (View Object)
    """

    knowledge: str
    evidence: list[str] = Field(default_factory=list)
    quote: dict[str, Any] | None = None
    symbol: str | None = None

    def to_prompt_context(self) -> str:
        """把技能结果格式化成可直接喂给提示词的上下文文本。"""
        quote_text = "无实时行情"
        if self.quote:
            quote_text = json.dumps(self.quote, ensure_ascii=False)

        evidence_text = "\n".join([f"- {item}" for item in self.evidence]) if self.evidence else "- 无"
        return (
            f"【检索知识】\n{self.knowledge or '无'}\n\n"
            f"【实时行情】\n{quote_text}\n\n"
            f"【证据清单】\n{evidence_text}"
        )


# --- 3. 核心编排逻辑（技能引擎） ---
class StockAnalysisSkill:
    """
    【高级技能：数据管家】 -> 【类比 Java】：这是一个 @Service 类
    """

    async def run(self, payload: StockAnalysisSkillInput) -> StockAnalysisSkillOutput:
        """执行技能主流程：查询检索资料、提取股票代码、补充行情与证据。"""
        logger.info(f"开始执行专家技能，目标问题：{payload.query}")

        merged_queries = [q.strip() for q in payload.queries if q and q.strip()]
        if payload.query.strip() not in merged_queries:
            merged_queries.append(payload.query.strip())

        logger.info(f"准备异步检索以下词条：{merged_queries}")
        knowledge = await run_retrieval_async(queries=merged_queries, mode="auto", top_k=payload.top_k)

        symbol = extract_stock_code(payload.query)

        # 教学修改：使用前端传过来的 Pydantic 参数来控制逻辑
        quote = None
        if symbol and payload.require_quote:
            logger.info(f"检测到股票代码 {symbol}，准备获取实时行情...")
            quote = self._get_quote_safe(symbol)

        evidence = self._build_evidence(merged_queries, symbol, bool(quote))

        logger.info("技能执行完毕，全部数据已打包")
        return StockAnalysisSkillOutput(
            knowledge=knowledge,
            evidence=evidence,
            quote=quote,
            symbol=symbol,
        )

    def _get_quote_safe(self, symbol: str) -> dict[str, Any] | None:
        """安全获取实时行情：失败返回 None，不向上抛异常。"""
        try:
            raw = get_stock_quote_core.invoke({"symbol": symbol})
            parsed = json.loads(raw)
            if isinstance(parsed, dict) and not parsed.get("error"):
                return parsed
        except Exception as e:
            logger.error(f"获取行情失败: {e}", exc_info=True)
            return None
        return None

    def _build_evidence(self, queries: list[str], symbol: str | None, has_quote: bool) -> list[str]:
        """组装证据清单，便于后续回答引用与审计追踪。"""
        items = [f"检索词: {q}" for q in queries[:3]]
        if symbol:
            items.append(f"识别股票代码: {symbol}")
            items.append("行情数据: 已命中" if has_quote else "行情数据: 未命中或接口失败")
        return items


# 【类比 Java】：可理解为 Spring 容器里的单例组件。
stock_analysis_skill = StockAnalysisSkill()

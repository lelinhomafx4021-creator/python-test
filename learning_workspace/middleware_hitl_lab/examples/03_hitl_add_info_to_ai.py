"""示例 03：同一个节点中暂停，补充新信息，然后把新信息加入 prompt 给 AI。

运行：
    D:\ai-investor\aipy2\.venv\Scripts\python.exe examples\03_hitl_add_info_to_ai.py

这个示例比 02 更接近真实业务：
    用户问："宁德时代能买吗？"
    AI 发现缺少投资周期，于是在同一个节点 interrupt。
    用户补充："中线，持有 3 个月左右"
    节点恢复后把补充信息拼进 prompt，再调用教学假模型。
"""

from __future__ import annotations

from pathlib import Path
from sys import path
from typing import TypedDict

from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt

path.append(str(Path(__file__).resolve().parent))
from teaching_fake_model import TeachingFakeModel


class AnalysisState(TypedDict):
    question: str
    investment_horizon: str
    prompt_sent_to_ai: str
    answer: str


model = TeachingFakeModel()


def analysis_node(state: AnalysisState) -> dict:
    """同一个节点：先暂停问人，再把人的回答加给 AI。"""

    print("[Node] analysis_node 开始执行")

    user_horizon = interrupt(
        {
            "type": "need_investment_horizon",
            "question": "你打算持有多久？",
            "options": [
                "短线，1-7 天",
                "中线，1-3 个月",
                "长线，6-12 个月",
            ],
        }
    )

    print(f"[Node] interrupt 恢复，用户补充: {user_horizon}")

    prompt = f"""
你是一个 AI 投研助手。

用户原始问题：
{state["question"]}

用户补充的投资周期：
{user_horizon}

请根据投资周期给出分析。短线重点看情绪和技术面，中线重点看趋势和业绩预期，长线重点看行业和估值。
"""

    ai_message = model.invoke([HumanMessage(content=prompt)])

    return {
        "investment_horizon": user_horizon,
        "prompt_sent_to_ai": prompt,
        "answer": ai_message.content,
    }


def build_graph():
    builder = StateGraph(AnalysisState)
    builder.add_node("analysis", analysis_node)
    builder.add_edge(START, "analysis")
    builder.add_edge("analysis", END)
    return builder.compile(checkpointer=InMemorySaver())


def main() -> None:
    app = build_graph()

    config = {
        "configurable": {
            "thread_id": "analysis-demo-user-001",
        }
    }

    print("\n========== 第一次调用：缺少投资周期，暂停 ==========")
    first_result = app.invoke(
        {
            "question": "宁德时代能买吗？",
            "investment_horizon": "",
            "prompt_sent_to_ai": "",
            "answer": "",
        },
        config=config,
    )
    print(first_result)

    print("\n========== 模拟用户补充：中线，1-3 个月 ==========")
    second_result = app.invoke(
        Command(resume="中线，1-3 个月"),
        config=config,
    )

    print("\n========== 发送给 AI 的 prompt ==========")
    print(second_result["prompt_sent_to_ai"])

    print("\n========== AI 最终回答 ==========")
    print(second_result["answer"])


if __name__ == "__main__":
    main()

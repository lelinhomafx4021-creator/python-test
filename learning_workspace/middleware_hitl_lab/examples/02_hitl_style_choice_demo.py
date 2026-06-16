"""示例 02：同一个 LangGraph 节点中暂停，让用户选择风格，然后继续。

运行：
    D:\ai-investor\aipy2\.venv\Scripts\python.exe examples\02_hitl_style_choice_demo.py

这个文件演示的是：
    - 第一次 invoke：图执行到 interrupt() 暂停。
    - 第二次 invoke：Command(resume=...) 把用户选择传回同一个节点。
    - 同一个节点继续生成最终答案。
"""

from __future__ import annotations

from typing import TypedDict

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt


class ReportState(TypedDict):
    question: str
    style: str
    answer: str


def write_report_node(state: ReportState) -> dict:
    """同一个节点里先问人，再继续生成。

    关键点：
    - 第一次执行到 interrupt() 时，图暂停。
    - 恢复时，这个节点会重新执行。
    - 但这次 interrupt() 不再暂停，而是返回 Command(resume=...) 的值。
    """

    print("[Node] write_report_node 开始执行")

    selected_style = interrupt(
        {
            "type": "style_choice",
            "question": "请选择报告风格",
            "options": ["保守稳健", "专业研报", "小白解释"],
            "default": "专业研报",
        }
    )

    print(f"[Node] interrupt 恢复，用户选择: {selected_style}")

    answer = (
        f"你选择了【{selected_style}】风格。\n"
        f"现在开始分析问题：{state['question']}\n"
        "这里如果接真实 LLM，就会把 style 放进 prompt。"
    )

    return {
        "style": selected_style,
        "answer": answer,
    }


def build_graph():
    builder = StateGraph(ReportState)
    builder.add_node("write_report", write_report_node)
    builder.add_edge(START, "write_report")
    builder.add_edge("write_report", END)
    return builder.compile(checkpointer=InMemorySaver())


def main() -> None:
    app = build_graph()

    config = {
        "configurable": {
            "thread_id": "style-demo-user-001",
        }
    }

    print("\n========== 第一次调用：会暂停 ==========")
    first_result = app.invoke(
        {
            "question": "帮我分析一下贵州茅台",
            "style": "",
            "answer": "",
        },
        config=config,
    )
    print(first_result)

    print("\n========== 模拟用户选择：专业研报 ==========")
    second_result = app.invoke(
        Command(resume="专业研报"),
        config=config,
    )
    print(second_result)


if __name__ == "__main__":
    main()

"""教学用假模型。

这个文件的目的不是模拟真实大模型能力，而是让 middleware 和 HITL 示例
可以在没有 API Key 的情况下跑通。
"""

from __future__ import annotations

from typing import Any, Sequence

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_core.tools import BaseTool


class TeachingFakeModel(BaseChatModel):
    """一个很小的 ChatModel。

    它支持 `bind_tools()`，所以可以被 `create_agent()` 使用。

    行为规则：
    - 如果还没有工具结果，并且 Agent 给它绑定了工具，它会要求调用第一个工具。
    - 如果已经有工具结果，它会基于工具结果返回最终回答。
    - 如果没有绑定工具，它会直接根据最后一条用户消息返回教学式回答。
    """

    bound_tool_names: list[str] = []
    model_label: str = "teaching-fake-model"

    @property
    def _llm_type(self) -> str:
        return self.model_label

    def bind_tools(self, tools: Sequence[BaseTool | dict | Any], **kwargs: Any):
        """让假模型兼容 LangChain tool calling。

        真实大模型的 bind_tools 会把工具 schema 发给模型。
        这里我们只记录工具名字，方便后面构造 tool_call。
        """

        tool_names: list[str] = []
        for tool in tools:
            if isinstance(tool, dict):
                tool_names.append(str(tool.get("name", "unknown_tool")))
            else:
                tool_names.append(str(getattr(tool, "name", "unknown_tool")))

        return self.model_copy(update={"bound_tool_names": tool_names})

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: Any | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        has_tool_result = any(type(message).__name__ == "ToolMessage" for message in messages)

        if self.bound_tool_names and not has_tool_result:
            message = AIMessage(
                content="",
                tool_calls=[
                    {
                        "name": self.bound_tool_names[0],
                        "args": {"symbol": "AAPL"},
                        "id": "call_get_stock_price",
                    }
                ],
            )
            return ChatResult(generations=[ChatGeneration(message=message)])

        if has_tool_result:
            tool_text = "\n".join(
                str(getattr(message, "content", ""))
                for message in messages
                if type(message).__name__ == "ToolMessage"
            )
            message = AIMessage(content=f"最终回答：我已经读取工具结果，结果是：{tool_text}")
            return ChatResult(generations=[ChatGeneration(message=message)])

        last_text = str(messages[-1].content) if messages else ""
        message = AIMessage(content=f"教学假模型收到 prompt：{last_text}")
        return ChatResult(generations=[ChatGeneration(message=message)])

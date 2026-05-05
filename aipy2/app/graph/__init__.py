"""
LangGraph 工作流引擎包。

拆分后的模块结构：
├── state.py          AgentState 状态定义 + 辅助函数
├── nodes.py          所有节点函数（意图识别、检索、回答、评审等）
├── routes.py         路由函数（条件边的决策逻辑）
├── investor_graph.py 图构建 + MultiGraphInvestorAgent 包装类
"""

from app.graph.state import AgentState
from app.graph.investor_graph import multi_graph_agent, build_self_rag_graph

__all__ = ["AgentState", "multi_graph_agent", "build_self_rag_graph"]

"""
集中管理业务异常。
知识点：企业级开发中，不直接抛出 ValueError 等内置异常，而是分层定义业务异常。
好处：方便统一拦截、统一返回格式、记录特定日志。
"""

class AiBaseException(Exception):
    """所有业务异常的基类"""
    def __init__(self, message: str, code: int = 500):
        self.message = message
        self.code = code
        super().__init__(message)

class GraphExecutionError(AiBaseException):
    """LangGraph 运行异常"""
    def __init__(self, message: str):
        super().__init__(message, code=5002)

class RetrievalError(AiBaseException):
    """检索工具异常"""
    def __init__(self, message: str):
        super().__init__(message, code=5003)

class AuthenticationError(AiBaseException):
    """身份校验异常"""
    def __init__(self, message: str):
        super().__init__(message, code=401)

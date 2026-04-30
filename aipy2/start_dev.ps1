$env:PYTHONIOENCODING = 'utf-8'

# [教学修改] Windows 下不要直接用 `uvicorn main:app` 命令启动。
# 原因：uvicorn CLI 会先创建默认事件循环，main.py 里后补的
# WindowsSelectorEventLoopPolicy 会来不及生效，psycopg 异步连接池就会报错。
# 改成 `uv run python main.py` 后，会先执行 main.py 顶部的兼容策略，再启动服务。
uv run python main.py

$env:PYTHONIOENCODING = 'utf-8'
uv run uvicorn main:app --host 0.0.0.0 --port 8000 --reload

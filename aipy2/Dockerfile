# Python AI Service
FROM python:3.12-slim
WORKDIR /app

# 安装 curl，给 Docker 健康检查用（见 docker-compose.prod.yml healthcheck）
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# 利用 Docker 层缓存：只改 pyproject.toml 才会重新装依赖
COPY pyproject.toml .
RUN pip install --no-cache-dir -e .

# 复制源码（.dockerignore 排除了 .venv、__pycache__、.env 等）
COPY . .
EXPOSE 8000
ENV APP_ENV=production
CMD ["python", "main.py"]

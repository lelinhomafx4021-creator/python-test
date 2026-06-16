# Repository Guidelines

## Project Structure & Module Organization
`frontend/` contains the Vue 3 + TypeScript client. Put page-level screens in `src/views`, reusable UI in `src/components`, shared API access in `src/api`, and composables in `src/composables`.

`java-ai-gateway/` is the Spring Boot gateway. Business code is organized by domain under `src/main/java/com/aiinvestor/gateway/modules/*`; database migrations live in `src/main/resources/db/migration`.

`aipy2/` is the FastAPI + LangGraph AI service. Routes live in `app/api/v1`, graph orchestration in `app/graph`, RAG logic in `app/rag`, tools in `app/tools`, migrations in `alembic/versions`, and tests in `tests/`.

`docs/` stores product and learning material. `docker-compose.yml` defines shared infrastructure, and `start_all.ps1` is the Windows bootstrap.

## Build, Test, and Development Commands
`.\start_all.ps1` starts Docker dependencies and all three services for local development.

`docker compose up -d` starts MySQL, Redis, RabbitMQ, Postgres, Langfuse, and Sentinel only.

`cd frontend && npm ci && npm run dev` starts the Vite app. Use `npm run build` for type-check + production build and `npm run lint` for ESLint.

`cd java-ai-gateway && mvn spring-boot:run` starts the gateway. Use `mvn test` for JUnit/Mockito tests.

`cd aipy2 && uv run alembic upgrade head` applies AI-side schema changes. Start with `uv run python main.py`; on Windows, prefer this over `uvicorn main:app`. Run tests with `uv run python -m pytest tests -v`.

## Coding Style & Naming Conventions
Vue and TypeScript use 2-space indentation, PascalCase component files like `TerminalOverview.vue`, and camelCase utilities such as `useMarketWebSocket.ts`.

Python uses 4-space indentation, snake_case modules, and `test_*.py` filenames.

Java uses 4-space indentation, package names under `com.aiinvestor.gateway`, and standard suffixes such as `*Controller`, `*Service`, `*Mapper`, `*DTO`, `*DO`, and `*VO`.

## Testing Guidelines
Java tests live in `java-ai-gateway/src/test/...` and generally use JUnit 5 with Mockito. Python tests live in `aipy2/tests/` and use `pytest` plus `pytest-asyncio`.

There is no established frontend test suite yet; for UI changes, run `npm run build` and include screenshots for major workflow updates. Keep GitHub Actions green: it currently runs Java tests and a frontend build.

## Commit & Pull Request Guidelines
Recent history favors short Chinese commit subjects that describe the shipped change directly, for example `新增系统公告功能、会员状态同步修复`. Keep subjects specific and scope-aware.

PRs should include a short summary, affected modules, migration or config notes, test evidence, and screenshots for visible UI changes.

## Security & Configuration Tips
Do not commit real secrets or local override files. Use `.env.prod.example`, environment variables, and local Spring overrides for credentials. Call out any new ports, queues, or schema migrations in the PR description.

"""
Simple gateway pressure test for interview demos.

Examples:
  python stress_test.py
  python stress_test.py --concurrency 20 --requests 100 --markdown-out docs/PRESSURE_TEST_RESULT.md
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path


DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_USERNAME = "admin"
DEFAULT_PASSWORD = "123456"
DEFAULT_CONCURRENCY = 20
DEFAULT_REQUESTS = 100


@dataclass
class TestResult:
    name: str
    total: int
    success: int
    failed: int
    avg_ms: float
    min_ms: float
    max_ms: float
    qps: float

    @property
    def success_rate(self) -> float:
        if self.total == 0:
            return 0.0
        return self.success / self.total * 100


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a simple pressure test against the Java gateway.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Gateway base URL")
    parser.add_argument("--username", default=DEFAULT_USERNAME, help="Login username")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="Login password")
    parser.add_argument("--concurrency", type=int, default=DEFAULT_CONCURRENCY, help="Concurrent workers")
    parser.add_argument("--requests", type=int, default=DEFAULT_REQUESTS, help="Requests per API")
    parser.add_argument(
        "--markdown-out",
        help="Optional markdown output path, for example docs/PRESSURE_TEST_RESULT.md",
    )
    return parser.parse_args()


def login(base_url: str, username: str, password: str) -> str:
    payload = json.dumps({"username": username, "password": password}).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url}/gateway/auth/login",
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["data"]["token"]


def request_once(url: str, token: str) -> tuple[float, bool]:
    started_at = time.perf_counter()
    try:
        request = urllib.request.Request(url, headers={"satoken": token})
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
            return time.perf_counter() - started_at, response.status == 200
    except (urllib.error.URLError, TimeoutError, OSError):
        return time.perf_counter() - started_at, False


def run_case(name: str, url: str, token: str, concurrency: int, total: int) -> TestResult:
    timings: list[float] = []
    success = 0
    failed = 0
    started_at = time.perf_counter()

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(request_once, url, token) for _ in range(total)]
        for future in as_completed(futures):
            elapsed, ok = future.result()
            timings.append(elapsed)
            if ok:
                success += 1
            else:
                failed += 1

    total_elapsed = time.perf_counter() - started_at
    return TestResult(
        name=name,
        total=total,
        success=success,
        failed=failed,
        avg_ms=sum(timings) / len(timings) * 1000,
        min_ms=min(timings) * 1000,
        max_ms=max(timings) * 1000,
        qps=total / total_elapsed if total_elapsed > 0 else 0.0,
    )


def build_markdown(
    results: list[TestResult],
    base_url: str,
    concurrency: int,
    requests: int,
) -> str:
    lines = [
        "# Pressure Test Result",
        "",
        f"- Target: `{base_url}`",
        f"- Concurrency: `{concurrency}`",
        f"- Requests per API: `{requests}`",
        "",
        "| API | Success Rate | Avg(ms) | Min(ms) | Max(ms) | QPS |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for result in results:
        lines.append(
            f"| `{result.name}` | {result.success_rate:.1f}% | {result.avg_ms:.1f} | "
            f"{result.min_ms:.1f} | {result.max_ms:.1f} | {result.qps:.0f} |"
        )
    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- This is a lightweight local pressure test for interview demonstration.",
            "- The result mainly reflects gateway interface stability, not full production capacity.",
        ]
    )
    return "\n".join(lines) + "\n"


def write_markdown(path_str: str, content: str) -> None:
    path = Path(path_str)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
    args = parse_args()
    print("AI Investor Pressure Test")
    print(f"Target: {args.base_url}")
    print(f"Concurrency: {args.concurrency}, Requests per API: {args.requests}\n")

    try:
        token = login(args.base_url, args.username, args.password)
    except Exception as exc:  # noqa: BLE001
        print(f"Login failed: {exc}")
        print("Make sure the Java gateway is running and the demo account is available.")
        return 1

    print(f"Login OK, token prefix: {token[:20]}...\n")

    test_cases = [
        ("auth/me", "/gateway/auth/me"),
        ("membership", "/api/v1/memberships/me"),
        ("quotas", "/api/v1/quotas/me"),
        ("quotes", "/api/v1/market/quotes?symbols=600519,000001"),
        ("sectors", "/api/v1/sectors"),
        ("watchlists", "/api/v1/watchlists"),
        ("paper", "/api/v1/paper/accounts/me"),
        ("announcements", "/api/v1/announcements"),
        ("notifications", "/api/v1/notifications"),
        ("ai/sessions", "/gateway/ai/sessions"),
    ]

    results: list[TestResult] = []
    for name, path in test_cases:
        print(f"Testing {name}...")
        result = run_case(
            name=name,
            url=f"{args.base_url}{path}",
            token=token,
            concurrency=args.concurrency,
            total=args.requests,
        )
        results.append(result)
        print(
            f"  OK: {result.success}/{result.total}, "
            f"Avg: {result.avg_ms:.1f}ms, QPS: {result.qps:.0f}"
        )

    print("\n" + "=" * 78)
    print(f"{'API':<20} {'Rate':>8} {'Avg(ms)':>10} {'Min(ms)':>10} {'Max(ms)':>10} {'QPS':>10}")
    print("-" * 78)
    for result in results:
        print(
            f"{result.name:<20} {result.success_rate:>7.1f}% "
            f"{result.avg_ms:>9.1f} {result.min_ms:>9.1f} {result.max_ms:>9.1f} {result.qps:>9.0f}"
        )

    if args.markdown_out:
        markdown = build_markdown(results, args.base_url, args.concurrency, args.requests)
        write_markdown(args.markdown_out, markdown)
        print(f"\nMarkdown report written to: {args.markdown_out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

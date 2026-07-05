"""
Lightweight smoke test for the interview demo flow.

Examples:
  python demo_smoke_test.py
  python demo_smoke_test.py --base-url http://127.0.0.1:8080 --username investor_zhang
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass


DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_ADMIN_USERNAME = "demo_admin"
DEFAULT_DEMO_USERNAME = "demo_vip"
DEFAULT_PASSWORD = "Demo123456"


@dataclass
class CheckResult:
    name: str
    ok: bool
    detail: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a smoke test against the local demo environment.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Gateway base URL")
    parser.add_argument("--admin-username", default=DEFAULT_ADMIN_USERNAME, help="Admin username")
    parser.add_argument("--username", default=DEFAULT_DEMO_USERNAME, help="Demo username")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="Password for both accounts")
    return parser.parse_args()


def request_json(
    method: str,
    url: str,
    headers: dict[str, str] | None = None,
    payload: dict[str, object] | None = None,
) -> dict[str, object]:
    body = None
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def login(base_url: str, username: str, password: str) -> str:
    response = request_json(
        "POST",
        f"{base_url}/gateway/auth/login",
        payload={"username": username, "password": password},
    )
    data = response["data"]
    if not isinstance(data, dict):
        raise ValueError(f"Unexpected login response for {username}: {response}")
    token = data.get("token")
    if not token:
        raise ValueError(f"Token missing in login response for {username}: {response}")
    return str(token)


def run_checks(base_url: str, admin_token: str, demo_token: str) -> list[CheckResult]:
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    demo_headers = {"Authorization": f"Bearer {demo_token}"}

    results: list[CheckResult] = []

    overview = request_json("GET", f"{base_url}/api/v1/admin/overview", headers=admin_headers)
    overview_data = overview.get("data") or {}
    total_users = int(overview_data.get("totalUsers", 0))
    total_ai_sessions = int(overview_data.get("totalAiSessions", 0))
    results.append(
        CheckResult(
            "admin_overview",
            total_users >= 6 and total_ai_sessions >= 5,
            f"totalUsers={total_users}, totalAiSessions={total_ai_sessions}",
        )
    )

    announcements = request_json("GET", f"{base_url}/api/v1/announcements", headers=demo_headers)
    announcement_items = announcements.get("data") or []
    results.append(
        CheckResult(
            "announcements",
            len(announcement_items) >= 3,
            f"count={len(announcement_items)}",
        )
    )

    watchlists = request_json("GET", f"{base_url}/api/v1/watchlists", headers=demo_headers)
    watchlist_items = watchlists.get("data") or []
    results.append(
        CheckResult(
            "watchlists",
            len(watchlist_items) >= 2,
            f"count={len(watchlist_items)}",
        )
    )

    paper_account = request_json("GET", f"{base_url}/api/v1/paper/accounts/me", headers=demo_headers)
    account_data = paper_account.get("data") or {}
    account_status = str(account_data.get("status", ""))
    results.append(
        CheckResult(
            "paper_account",
            account_status == "active",
            f"accountNo={account_data.get('accountNo')}, status={account_status}",
        )
    )

    ai_sessions = request_json("GET", f"{base_url}/gateway/ai/sessions", headers=demo_headers)
    session_items = ai_sessions.get("data") or []
    results.append(
        CheckResult(
            "ai_sessions",
            len(session_items) >= 1,
            f"count={len(session_items)}",
        )
    )

    membership = request_json("GET", f"{base_url}/api/v1/memberships/me", headers=demo_headers)
    membership_data = membership.get("data") or {}
    plan_code = str(membership_data.get("planCode", ""))
    results.append(
        CheckResult(
            "membership",
            plan_code in {"free", "vip"},
            f"planCode={plan_code}",
        )
    )

    notifications = request_json("GET", f"{base_url}/api/v1/notifications", headers=demo_headers)
    notification_items = notifications.get("data") or []
    results.append(
        CheckResult(
            "notifications",
            len(notification_items) >= 1,
            f"count={len(notification_items)}",
        )
    )

    return results


def main() -> int:
    args = parse_args()
    print("AI Investor Demo Smoke Test")
    print(f"Base URL: {args.base_url}")
    print(f"Admin: {args.admin_username}")
    print(f"Demo User: {args.username}\n")

    try:
        admin_token = login(args.base_url, args.admin_username, args.password)
        demo_token = login(args.base_url, args.username, args.password)
        results = run_checks(args.base_url, admin_token, demo_token)
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, KeyError) as exc:
        print(f"Smoke test failed: {exc}")
        print("Make sure services are running and the seed data has been loaded.")
        return 1

    failed = [result for result in results if not result.ok]
    for result in results:
        prefix = "PASS" if result.ok else "FAIL"
        print(f"[{prefix}] {result.name:<16} {result.detail}")

    if failed:
        print(f"\nSmoke test finished with {len(failed)} failed checks.")
        return 1

    print(f"\nSmoke test passed with {len(results)} checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""
AI Investor 压力测试脚本
"""

import urllib.request
import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass

BASE_URL = "http://127.0.0.1:8080"
CONCURRENCY = 50
REQUESTS = 500

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

def login():
    data = json.dumps({"username": "demo", "password": "123456"}).encode()
    req = urllib.request.Request(f"{BASE_URL}/gateway/auth/login", data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())["data"]["token"]

def request(url, token):
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, headers={"satoken": token})
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp.read()
            return time.perf_counter() - start, resp.status == 200
    except:
        return time.perf_counter() - start, False

def test(name, url, token, concurrency, total):
    times, ok, fail = [], 0, 0
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        for t, s in [f.result() for f in as_completed([ex.submit(request, url, token) for _ in range(total)])]:
            times.append(t)
            if s: ok += 1
            else: fail += 1
    elapsed = time.perf_counter() - start
    return TestResult(name, total, ok, fail, sum(times)/len(times)*1000, min(times)*1000, max(times)*1000, total/elapsed)

def main():
    print(f"AI Investor Stress Test")
    print(f"Concurrency: {CONCURRENCY}, Requests: {REQUESTS}")
    print(f"Target: {BASE_URL}\n")

    token = login()
    print(f"Login OK, token: {token[:20]}...\n")

    tests = [
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

    results = []
    for name, path in tests:
        print(f"Testing {name}...")
        r = test(name, f"{BASE_URL}{path}", token, CONCURRENCY, REQUESTS)
        results.append(r)
        print(f"  OK: {r.success}/{r.total}, Avg: {r.avg_ms:.1f}ms, QPS: {r.qps:.0f}\n")

    print("="*70)
    print(f"{'API':<20} {'Rate':>8} {'Avg(ms)':>10} {'QPS':>10}")
    print("-"*70)
    for r in results:
        print(f"{r.name:<20} {r.success/r.total*100:>7.1f}% {r.avg_ms:>9.1f} {r.qps:>9.0f}")

if __name__ == "__main__":
    main()

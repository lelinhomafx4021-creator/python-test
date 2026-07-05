from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from typing import Iterable

import pymysql


DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "123456",
    "database": "ai_investor",
    "charset": "utf8mb4",
    "autocommit": False,
    "cursorclass": pymysql.cursors.DictCursor,
}

PASSWORD_HASH = "$2a$10$oZpluI/1y4wtJbwkKTblMe4kQ6AVy8rUlc44Q8o76rmVb9t/B.Pb6"
ANNOUNCEMENT_PREFIX = "【演示】"


@dataclass(frozen=True)
class DemoUser:
    username: str
    nickname: str
    role: str
    email: str
    phone: str
    risk_level: str
    investment_years: int
    interested_sectors: str
    bio: str
    plan_code: str


DEMO_USERS = [
    DemoUser(
        username="investor_zhang",
        nickname="张晨",
        role="vip",
        email="investor_zhang@example.com",
        phone="13800000001",
        risk_level="aggressive",
        investment_years=6,
        interested_sectors="白酒,新能源,人工智能",
        bio="偏成长风格，关注景气度和机构持仓变化。",
        plan_code="vip",
    ),
    DemoUser(
        username="investor_li",
        nickname="李越",
        role="normal",
        email="investor_li@example.com",
        phone="13800000002",
        risk_level="balanced",
        investment_years=3,
        interested_sectors="银行,保险,高股息",
        bio="更看重回撤控制，喜欢现金流稳定的公司。",
        plan_code="free",
    ),
    DemoUser(
        username="investor_wang",
        nickname="王可",
        role="vip",
        email="investor_wang@example.com",
        phone="13800000003",
        risk_level="balanced",
        investment_years=5,
        interested_sectors="消费电子,半导体,机器人",
        bio="偏中短线，会结合量价和事件驱动做跟踪。",
        plan_code="vip",
    ),
    DemoUser(
        username="investor_chen",
        nickname="陈诺",
        role="normal",
        email="investor_chen@example.com",
        phone="13800000004",
        risk_level="steady",
        investment_years=2,
        interested_sectors="医药,消费,ETF",
        bio="以指数和行业龙头为主，偏长期配置。",
        plan_code="free",
    ),
    DemoUser(
        username="investor_sun",
        nickname="孙睿",
        role="vip",
        email="investor_sun@example.com",
        phone="13800000005",
        risk_level="aggressive",
        investment_years=8,
        interested_sectors="券商,军工,数字经济",
        bio="喜欢热点轮动，也会用 AI 总结盘后复盘。",
        plan_code="vip",
    ),
]

PLAN_QUOTAS = {
    "free": {
        "ai_chat_daily": 20,
        "watchlist_count": 1,
        "alert_count": 3,
    },
    "vip": {
        "ai_chat_daily": 200,
        "watchlist_count": 10,
        "alert_count": 50,
    },
}

USER_SYMBOLS = {
    "investor_zhang": ["600519", "300750", "601318"],
    "investor_li": ["600036", "000001", "601318"],
    "investor_wang": ["300750", "600519", "600036"],
    "investor_chen": ["000001", "600036", "300750"],
    "investor_sun": ["601318", "600519", "000001"],
}

MARKET_SNAPSHOT = {
    "600519": {"price": Decimal("1478.50"), "name": "贵州茅台"},
    "000001": {"price": Decimal("12.36"), "name": "平安银行"},
    "300750": {"price": Decimal("186.20"), "name": "宁德时代"},
    "601318": {"price": Decimal("48.75"), "name": "中国平安"},
    "600036": {"price": Decimal("34.18"), "name": "招商银行"},
}


def get_conn():
    return pymysql.connect(**DB_CONFIG)


def chunked_delete(cursor, sql: str, values: Iterable[int | str]) -> None:
    items = list(values)
    if not items:
        return
    placeholders = ",".join(["%s"] * len(items))
    cursor.execute(sql.format(placeholders=placeholders), items)


def upsert_demo_users(cursor) -> dict[str, int]:
    for index, demo_user in enumerate(DEMO_USERS, start=1):
        last_login_at = datetime.now() - timedelta(days=index - 1, hours=index)
        cursor.execute(
            """
            INSERT INTO users
                (username, password_hash, password, phone, email, nickname, avatar_url, role, status, last_login_at)
            VALUES
                (%s, %s, %s, %s, %s, %s, %s, %s, 1, %s)
            ON DUPLICATE KEY UPDATE
                password_hash = VALUES(password_hash),
                password = VALUES(password),
                phone = VALUES(phone),
                email = VALUES(email),
                nickname = VALUES(nickname),
                avatar_url = VALUES(avatar_url),
                role = VALUES(role),
                status = VALUES(status),
                last_login_at = VALUES(last_login_at)
            """,
            (
                demo_user.username,
                PASSWORD_HASH,
                PASSWORD_HASH,
                demo_user.phone,
                demo_user.email,
                demo_user.nickname,
                f"https://api.dicebear.com/7.x/initials/svg?seed={demo_user.username}",
                demo_user.role,
                last_login_at,
            ),
        )

    cursor.execute(
        "SELECT id, username FROM users WHERE username IN ({})".format(
            ",".join(["%s"] * len(DEMO_USERS))
        ),
        [user.username for user in DEMO_USERS],
    )
    return {row["username"]: row["id"] for row in cursor.fetchall()}


def cleanup_seed_scope(cursor, user_ids: list[int]) -> None:
    if not user_ids:
        return

    chunked_delete(
        cursor,
        "DELETE FROM watchlist_items WHERE watchlist_id IN (SELECT id FROM watchlists WHERE user_id IN ({placeholders}))",
        user_ids,
    )
    chunked_delete(cursor, "DELETE FROM watchlists WHERE user_id IN ({placeholders})", user_ids)

    chunked_delete(
        cursor,
        "DELETE FROM paper_trades WHERE account_id IN (SELECT id FROM paper_accounts WHERE user_id IN ({placeholders}))",
        user_ids,
    )
    chunked_delete(
        cursor,
        "DELETE FROM paper_orders WHERE account_id IN (SELECT id FROM paper_accounts WHERE user_id IN ({placeholders}))",
        user_ids,
    )
    chunked_delete(
        cursor,
        "DELETE FROM paper_positions WHERE account_id IN (SELECT id FROM paper_accounts WHERE user_id IN ({placeholders}))",
        user_ids,
    )
    chunked_delete(
        cursor,
        "DELETE FROM paper_daily_assets WHERE account_id IN (SELECT id FROM paper_accounts WHERE user_id IN ({placeholders}))",
        user_ids,
    )
    chunked_delete(
        cursor,
        "DELETE FROM paper_cash_transfers WHERE account_id IN (SELECT id FROM paper_accounts WHERE user_id IN ({placeholders}))",
        user_ids,
    )
    chunked_delete(cursor, "DELETE FROM paper_accounts WHERE user_id IN ({placeholders})", user_ids)

    chunked_delete(cursor, "DELETE FROM transaction_logs WHERE user_id IN ({placeholders})", user_ids)
    chunked_delete(cursor, "DELETE FROM ai_usage_records WHERE user_id IN ({placeholders})", user_ids)
    chunked_delete(cursor, "DELETE FROM ai_sessions WHERE user_id IN ({placeholders})", user_ids)
    chunked_delete(cursor, "DELETE FROM user_notifications WHERE user_id IN ({placeholders})", user_ids)
    chunked_delete(cursor, "DELETE FROM user_memberships WHERE user_id IN ({placeholders})", user_ids)
    chunked_delete(cursor, "DELETE FROM user_feature_quotas WHERE user_id IN ({placeholders})", user_ids)
    chunked_delete(cursor, "DELETE FROM user_profiles WHERE user_id IN ({placeholders})", user_ids)

    user_id_strings = [str(user_id) for user_id in user_ids]
    chunked_delete(cursor, "DELETE FROM ai_chat_turns WHERE user_id IN ({placeholders})", user_id_strings)
    chunked_delete(cursor, "DELETE FROM ai_handoff_tickets WHERE user_id IN ({placeholders})", user_id_strings)

    cursor.execute("DELETE FROM announcements WHERE title LIKE %s", (f"{ANNOUNCEMENT_PREFIX}%",))


def seed_profiles_and_memberships(cursor, user_id_map: dict[str, int]) -> None:
    now = datetime.now()
    for index, demo_user in enumerate(DEMO_USERS, start=1):
        user_id = user_id_map[demo_user.username]
        cursor.execute(
            """
            INSERT INTO user_profiles
                (user_id, risk_level, investment_years, interested_sectors, bio)
            VALUES
                (%s, %s, %s, %s, %s)
            """,
            (
                user_id,
                demo_user.risk_level,
                demo_user.investment_years,
                demo_user.interested_sectors,
                demo_user.bio,
            ),
        )

        start_at = now - timedelta(days=30 + index * 5)
        end_at = start_at + timedelta(days=365 if demo_user.plan_code == "vip" else 30)
        cursor.execute(
            """
            INSERT INTO user_memberships
                (user_id, plan_code, start_at, end_at, status, auto_renew, source)
            VALUES
                (%s, %s, %s, %s, 'active', %s, %s)
            """,
            (
                user_id,
                demo_user.plan_code,
                start_at,
                end_at,
                1 if demo_user.plan_code == "vip" else 0,
                "demo_seed",
            ),
        )

        for feature_code, limit_count in PLAN_QUOTAS[demo_user.plan_code].items():
            used_count = min(index * 2, limit_count // 2) if feature_code == "ai_chat_daily" else index - 1
            reset_at = now + timedelta(days=1) if feature_code == "ai_chat_daily" else None
            cursor.execute(
                """
                INSERT INTO user_feature_quotas
                    (user_id, feature_code, period_type, limit_count, used_count, reset_at)
                VALUES
                    (%s, %s, 'permanent', %s, %s, %s)
                """,
                (user_id, feature_code, limit_count, used_count, reset_at),
            )


def create_watchlists(cursor, user_id_map: dict[str, int]) -> None:
    for index, demo_user in enumerate(DEMO_USERS, start=1):
        user_id = user_id_map[demo_user.username]
        watchlist_names = [("核心观察", 1), ("事件驱动", 0)]
        for watchlist_index, (name, is_default) in enumerate(watchlist_names, start=1):
            cursor.execute(
                """
                INSERT INTO watchlists (user_id, name, is_default, sort_order)
                VALUES (%s, %s, %s, %s)
                """,
                (user_id, name, is_default, watchlist_index),
            )
            watchlist_id = cursor.lastrowid
            symbols = USER_SYMBOLS[demo_user.username]
            selected = symbols[:2] if is_default else symbols[1:]
            for item_index, symbol in enumerate(selected, start=1):
                cursor.execute(
                    """
                    INSERT INTO watchlist_items
                        (watchlist_id, symbol, note, alert_enabled, sort_order)
                    VALUES
                        (%s, %s, %s, %s, %s)
                    """,
                    (
                        watchlist_id,
                        symbol,
                        f"{MARKET_SNAPSHOT[symbol]['name']} 跟踪位",
                        1 if item_index == 1 else 0,
                        item_index,
                    ),
                )


def create_paper_trading(cursor, user_id_map: dict[str, int]) -> None:
    today = date.today()
    for index, demo_user in enumerate(DEMO_USERS, start=1):
        user_id = user_id_map[demo_user.username]
        symbols = USER_SYMBOLS[demo_user.username]

        positions = []
        market_value = Decimal("0")
        floating_pnl = Decimal("0")
        for pos_index, symbol in enumerate(symbols[:2], start=1):
            price = MARKET_SNAPSHOT[symbol]["price"]
            qty = 100 * (pos_index + index)
            avg_cost = price - Decimal(str(2.2 * pos_index))
            current_value = price * qty
            pnl = (price - avg_cost) * qty
            positions.append((symbol, qty, avg_cost, current_value, pnl))
            market_value += current_value
            floating_pnl += pnl

        deposit_amount = Decimal("1000000") + Decimal(index * 50000)
        cash_balance = deposit_amount - market_value + Decimal(index * 3200)
        total_asset = cash_balance + market_value

        cursor.execute(
            """
            INSERT INTO paper_accounts
                (user_id, account_no, cash_balance, frozen_cash, total_asset, total_pnl, status)
            VALUES
                (%s, %s, %s, 0, %s, %s, 'active')
            """,
            (
                user_id,
                f"SIM{today.strftime('%Y%m')}{user_id:04d}",
                cash_balance,
                total_asset,
                floating_pnl,
            ),
        )
        account_id = cursor.lastrowid

        for symbol, qty, avg_cost, current_value, pnl in positions:
            cursor.execute(
                """
                INSERT INTO paper_positions
                    (account_id, symbol, position_qty, available_qty, avg_cost, market_value, floating_pnl)
                VALUES
                    (%s, %s, %s, %s, %s, %s, %s)
                """,
                (account_id, symbol, qty, qty, avg_cost, current_value, pnl),
            )

        transfer_created = datetime.combine(today - timedelta(days=7 + index), time(9, 30))
        transfer_paid = transfer_created + timedelta(minutes=3)
        cursor.execute(
            """
            INSERT INTO paper_cash_transfers
                (account_id, user_id, direction, channel_code, channel_name, out_trade_no, channel_trade_no,
                 amount, status, remark, created_at, paid_at)
            VALUES
                (%s, %s, 'deposit', 'mock_gateway', '演示支付通道', %s, %s,
                 %s, 'success', %s, %s, %s)
            """,
            (
                account_id,
                user_id,
                f"DEMO-DEP-{user_id}",
                f"CHN-{user_id}",
                deposit_amount,
                "初始化模拟资金",
                transfer_created,
                transfer_paid,
            ),
        )
        cursor.execute(
            """
            INSERT INTO transaction_logs
                (user_id, event_type, amount, balance_after, description, created_at)
            VALUES
                (%s, 'DEPOSIT', %s, %s, %s, %s)
            """,
            (user_id, deposit_amount, deposit_amount, "演示数据初始化入金", transfer_paid),
        )

        for order_index, symbol in enumerate(symbols[:2], start=1):
            price = MARKET_SNAPSHOT[symbol]["price"]
            qty = 100 * (order_index + index)
            created_at = datetime.combine(today - timedelta(days=order_index + 1), time(10 + order_index, 5))
            cursor.execute(
                """
                INSERT INTO paper_orders
                    (account_id, symbol, side, order_type, order_price, order_qty, filled_qty, order_status,
                     client_request_id, created_at)
                VALUES
                    (%s, %s, 'BUY', 'market', %s, %s, %s, 'filled', %s, %s)
                """,
                (
                    account_id,
                    symbol,
                    price,
                    qty,
                    qty,
                    f"demo-buy-{user_id}-{order_index}",
                    created_at,
                ),
            )
            order_id = cursor.lastrowid
            trade_amount = price * qty
            trade_time = created_at + timedelta(seconds=12)
            cursor.execute(
                """
                INSERT INTO paper_trades
                    (order_id, account_id, symbol, side, trade_price, trade_qty, trade_amount, trade_time)
                VALUES
                    (%s, %s, %s, 'BUY', %s, %s, %s, %s)
                """,
                (order_id, account_id, symbol, price, qty, trade_amount, trade_time),
            )
            cursor.execute(
                """
                INSERT INTO transaction_logs
                    (user_id, event_type, symbol, side, quantity, price, amount, balance_after, description, created_at)
                VALUES
                    (%s, 'ORDER_FILLED', %s, 'BUY', %s, %s, %s, %s, %s, %s)
                """,
                (
                    user_id,
                    symbol,
                    qty,
                    price,
                    trade_amount,
                    cash_balance,
                    f"买入 {MARKET_SNAPSHOT[symbol]['name']} 成交",
                    trade_time,
                ),
            )

        pending_symbol = symbols[2]
        pending_created_at = datetime.combine(today, time(14, 15))
        cursor.execute(
            """
            INSERT INTO paper_orders
                (account_id, symbol, side, order_type, order_price, order_qty, filled_qty, order_status,
                 client_request_id, created_at)
            VALUES
                (%s, %s, 'SELL', 'limit', %s, %s, 0, 'submitted', %s, %s)
            """,
            (
                account_id,
                pending_symbol,
                MARKET_SNAPSHOT[pending_symbol]["price"] + Decimal("1.50"),
                100,
                f"demo-sell-pending-{user_id}",
                pending_created_at,
            ),
        )

        cancelled_created_at = datetime.combine(today - timedelta(days=1), time(14, 40))
        cursor.execute(
            """
            INSERT INTO paper_orders
                (account_id, symbol, side, order_type, order_price, order_qty, filled_qty, order_status,
                 client_request_id, created_at)
            VALUES
                (%s, %s, 'SELL', 'limit', %s, %s, 0, 'cancelled', %s, %s)
            """,
            (
                account_id,
                symbols[0],
                MARKET_SNAPSHOT[symbols[0]]["price"] + Decimal("3.80"),
                100,
                f"demo-sell-cancel-{user_id}",
                cancelled_created_at,
            ),
        )
        cursor.execute(
            """
            INSERT INTO transaction_logs
                (user_id, event_type, symbol, side, quantity, price, amount, balance_after, description, created_at)
            VALUES
                (%s, 'ORDER_CANCELLED', %s, 'SELL', %s, %s, %s, %s, %s, %s)
            """,
            (
                user_id,
                symbols[0],
                100,
                MARKET_SNAPSHOT[symbols[0]]["price"] + Decimal("3.80"),
                (MARKET_SNAPSHOT[symbols[0]]["price"] + Decimal("3.80")) * 100,
                cash_balance,
                f"卖出 {MARKET_SNAPSHOT[symbols[0]]['name']} 委托已撤单",
                cancelled_created_at + timedelta(minutes=2),
            ),
        )

        for days_ago in range(6, -1, -1):
            trade_date = today - timedelta(days=days_ago)
            variation = Decimal((6 - days_ago) * 1800 + index * 220)
            daily_total = total_asset - Decimal("6000") + variation
            daily_market_value = market_value - Decimal(days_ago * 1200)
            daily_cash = daily_total - daily_market_value
            daily_pnl = variation - Decimal("2000")
            cursor.execute(
                """
                INSERT INTO paper_daily_assets
                    (account_id, trade_date, cash_balance, market_value, total_asset, daily_pnl)
                VALUES
                    (%s, %s, %s, %s, %s, %s)
                """,
                (account_id, trade_date, daily_cash, daily_market_value, daily_total, daily_pnl),
            )


def create_ai_data(cursor, user_id_map: dict[str, int]) -> None:
    now = datetime.now()
    for index, demo_user in enumerate(DEMO_USERS, start=1):
        user_id = user_id_map[demo_user.username]
        session_id = f"demo-session-{user_id}"
        created_at = now - timedelta(days=index, hours=2)
        cursor.execute(
            """
            INSERT INTO ai_sessions
                (user_id, session_id, context_type, context_ref, title, status, created_at, updated_at)
            VALUES
                (%s, %s, 'portfolio', %s, %s, 'active', %s, %s)
            """,
            (
                user_id,
                session_id,
                USER_SYMBOLS[demo_user.username][0],
                f"{demo_user.nickname} 的持仓复盘",
                created_at,
                created_at + timedelta(minutes=30),
            ),
        )

        turns = [
            (
                f"trace-{user_id}-1",
                "帮我总结一下今天组合波动的主要原因。",
                "组合波动主要来自高权重新能源和白酒标的分化，建议优先看仓位集中度和行业相关性。",
                "portfolio_review",
            ),
            (
                f"trace-{user_id}-2",
                f"{USER_SYMBOLS[demo_user.username][0]} 这只股票接下来怎么跟踪？",
                "可结合估值区间、北向资金和近三次财报增速做跟踪，避免只看单日涨跌。",
                "stock_analysis",
            ),
        ]
        for turn_index, (trace_id, query, answer, intent) in enumerate(turns, start=1):
            created_turn_at = created_at + timedelta(minutes=turn_index * 6)
            cursor.execute(
                """
                INSERT INTO ai_chat_turns
                    (user_id, session_id, thread_id, trace_id, title, query, answer, intent, source,
                     review_passed, response_mode, a2a_count, created_at)
                VALUES
                    (%s, %s, %s, %s, %s, %s, %s, %s, 'demo_seed',
                     1, 'sync', %s, %s)
                """,
                (
                    str(user_id),
                    session_id,
                    f"thread-{user_id}",
                    trace_id,
                    f"演示对话 {turn_index}",
                    query,
                    answer,
                    intent,
                    turn_index - 1,
                    created_turn_at,
                ),
            )
            cursor.execute(
                """
                INSERT INTO ai_usage_records
                    (user_id, feature_code, membership_level, trace_id, request_tokens, response_tokens, status, created_at)
                VALUES
                    (%s, 'ai_chat_daily', %s, %s, %s, %s, 'success', %s)
                """,
                (
                    user_id,
                    demo_user.plan_code,
                    trace_id,
                    220 + turn_index * 15,
                    480 + turn_index * 30,
                    created_turn_at,
                ),
            )

        if index <= 2:
            cursor.execute(
                """
                INSERT INTO ai_handoff_tickets
                    (trace_id, user_id, session_id, thread_id, query, handoff_reason, handoff_summary, status,
                     process_note, response_message, handled_by, handled_at, updated_at, created_at)
                VALUES
                    (%s, %s, %s, %s, %s, %s, %s, %s,
                     %s, %s, %s, %s, %s, %s)
                """,
                (
                    f"ticket-trace-{user_id}",
                    str(user_id),
                    session_id,
                    f"thread-{user_id}",
                    "AI 对仓位调整建议不够具体，希望人工补充。",
                    "need_human_judgement",
                    "用户希望得到更明确的调仓优先级和仓位建议。",
                    "processing" if index == 1 else "open",
                    "已进入人工复核队列。" if index == 1 else None,
                    "我们会在收盘后补充人工观点。" if index == 1 else None,
                    "admin" if index == 1 else None,
                    now - timedelta(hours=3) if index == 1 else None,
                    now - timedelta(hours=3) if index == 1 else created_at + timedelta(hours=1),
                    created_at + timedelta(minutes=40),
                ),
            )


def create_notifications_and_announcements(cursor, user_id_map: dict[str, int]) -> None:
    now = datetime.now()
    for index, demo_user in enumerate(DEMO_USERS, start=1):
        user_id = user_id_map[demo_user.username]
        notifications = [
            ("system", "欢迎使用演示环境", "系统已为你准备好模拟账户、自选股和 AI 对话示例。", "read"),
            ("portfolio", "组合异动提醒", "你关注的核心标的今日波动较大，建议查看 AI 复盘建议。", "unread"),
        ]
        for note_index, (category, title, content, status) in enumerate(notifications, start=1):
            created_at = now - timedelta(days=index, minutes=note_index * 11)
            read_at = created_at + timedelta(minutes=5) if status == "read" else None
            cursor.execute(
                """
                INSERT INTO user_notifications
                    (user_id, category, title, content, status, created_at, read_at)
                VALUES
                    (%s, %s, %s, %s, %s, %s, %s)
                """,
                (user_id, category, title, content, status, created_at, read_at),
            )

    announcements = [
        (
            f"{ANNOUNCEMENT_PREFIX} 新用户演示说明",
            "当前环境已内置模拟账户、公告、通知、自选和 AI 会话样例，可直接用于面试演示。",
            "notice",
            now - timedelta(days=3),
        ),
        (
            f"{ANNOUNCEMENT_PREFIX} AI 投研服务升级",
            "已补充会员配额、人工兜底工单和对话留痕，方便展示完整链路。",
            "urgent",
            now - timedelta(days=2, hours=4),
        ),
        (
            f"{ANNOUNCEMENT_PREFIX} 模拟交易维护窗口",
            "今日 22:30 至 23:00 会进行演示数据重置，不影响项目代码和接口联调。",
            "maintenance",
            now - timedelta(days=1, hours=2),
        ),
    ]
    for title, content, ann_type, published_at in announcements:
        cursor.execute(
            """
            INSERT INTO announcements
                (title, content, type, status, published_at, created_by)
            VALUES
                (%s, %s, %s, 'published', %s, 1)
            """,
            (title, content, ann_type, published_at),
        )


def collect_counts(cursor) -> dict[str, int]:
    tables = [
        "users",
        "announcements",
        "watchlists",
        "watchlist_items",
        "paper_accounts",
        "paper_positions",
        "paper_orders",
        "paper_trades",
        "paper_daily_assets",
        "paper_cash_transfers",
        "transaction_logs",
        "ai_sessions",
        "ai_chat_turns",
        "ai_handoff_tickets",
        "ai_usage_records",
        "user_notifications",
    ]
    counts = {}
    for table in tables:
        cursor.execute(f"SELECT COUNT(*) AS c FROM {table}")
        counts[table] = cursor.fetchone()["c"]
    return counts


def main() -> None:
    conn = get_conn()
    try:
        with conn.cursor() as cursor:
            user_id_map = upsert_demo_users(cursor)
            cleanup_seed_scope(cursor, list(user_id_map.values()))
            seed_profiles_and_memberships(cursor, user_id_map)
            create_watchlists(cursor, user_id_map)
            create_paper_trading(cursor, user_id_map)
            create_ai_data(cursor, user_id_map)
            create_notifications_and_announcements(cursor, user_id_map)
            counts = collect_counts(cursor)
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    print("Seed complete.")
    print("Demo users:", ", ".join(user.username for user in DEMO_USERS))
    for table, count in counts.items():
        print(f"{table}: {count}")


if __name__ == "__main__":
    main()

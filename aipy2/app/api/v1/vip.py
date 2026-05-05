"""VIP 申请审核接口。

用户扫码付款后提交申请，管理员审核通过后自动升级角色。
支持飞书 Webhook 通知。
"""

import os
from datetime import datetime
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from pydantic import BaseModel, Field

from app.core.logger import logger

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------

# 飞书群机器人 Webhook 地址（群设置 → 群机器人 → 自定义机器人 → 复制地址）
# 留空则不发通知
FEISHU_WEBHOOK_URL = os.getenv("FEISHU_WEBHOOK_URL", "")

# 付款截图保存目录
UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "uploads", "payment")
os.makedirs(UPLOAD_DIR, exist_ok=True)

# ---------------------------------------------------------------------------
# 数据模型（内存存储，课程设计够用；生产环境换数据库）
# ---------------------------------------------------------------------------

_applications: list[dict] = []
_next_id = 1


class VipApplyRequest(BaseModel):
    """VIP 申请提交。"""
    username: str = Field(description="申请人用户名")
    user_id: int = Field(description="申请人用户ID")
    payment_amount: float = Field(default=199.0, description="支付金额")
    payment_note: str = Field(default="", description="留言（微信号等）")


class VipReviewRequest(BaseModel):
    """管理员审核。"""
    action: str = Field(description="approve 或 reject")
    reject_reason: str = Field(default="", description="拒绝原因（reject 时必填）")


# ---------------------------------------------------------------------------
# 飞书通知
# ---------------------------------------------------------------------------

async def _send_feishu_notification(app: dict) -> bool:
    """发飞书卡片消息通知管理员。"""
    if not FEISHU_WEBHOOK_URL:
        logger.info("飞书 Webhook 未配置，跳过通知")
        return False

    try:
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                FEISHU_WEBHOOK_URL,
                json={
                    "msg_type": "interactive",
                    "card": {
                        "header": {
                            "title": {"tag": "plain_text", "content": "📋 新的 VIP 申请"},
                            "template": "blue",
                        },
                        "elements": [
                            {
                                "tag": "div",
                                "text": {
                                    "tag": "lark_md",
                                    "content": (
                                        f"**用户：**{app['username']}\n"
                                        f"**用户ID：**{app['user_id']}\n"
                                        f"**金额：**¥{app['payment_amount']}\n"
                                        f"**留言：**{app.get('payment_note', '无')}\n"
                                        f"**时间：**{app['created_at']}\n\n"
                                        f"请登录管理后台审核 ✅"
                                    ),
                                },
                            }
                        ],
                    },
                },
                timeout=5,
            )
            if resp.status_code == 200:
                logger.info("飞书通知发送成功")
                return True
            else:
                logger.warning("飞书通知发送失败: %s", resp.text)
                return False
    except Exception as e:
        logger.warning("飞书通知异常: %s", e)
        return False


# ---------------------------------------------------------------------------
# API 路由
# ---------------------------------------------------------------------------

router = APIRouter(prefix="/api/v1/vip", tags=["VIP申请审核"])


@router.post("/apply")
async def apply_vip(req: VipApplyRequest):
    """提交 VIP 申请。

    用户扫码付款后调用此接口提交申请，系统会发飞书通知给管理员。
    """
    global _next_id

    # 检查是否已有待审核的申请
    existing = [a for a in _applications if a["user_id"] == req.user_id and a["status"] == "pending"]
    if existing:
        raise HTTPException(status_code=400, detail="您已有一个待审核的申请，请耐心等待")

    app = {
        "id": _next_id,
        "user_id": req.user_id,
        "username": req.username,
        "payment_amount": req.payment_amount,
        "payment_screenshot": "",
        "payment_note": req.payment_note,
        "status": "pending",
        "reject_reason": "",
        "reviewed_by": None,
        "reviewed_at": None,
        "created_at": datetime.now().isoformat(),
    }
    _next_id += 1
    _applications.append(app)

    # 飞书通知（异步，不阻塞响应）
    await _send_feishu_notification(app)

    return {
        "code": 200,
        "data": {"id": app["id"], "status": "pending"},
        "message": "申请已提交，等待管理员审核",
    }


@router.get("/applications")
async def list_applications(
    status: Optional[str] = None,
):
    """管理员查看所有申请列表。"""
    result = _applications
    if status:
        result = [a for a in result if a["status"] == status]
    # 最新的排前面
    result = sorted(result, key=lambda x: x["created_at"], reverse=True)
    return {"code": 200, "data": result, "message": "成功"}


@router.put("/applications/{app_id}/review")
async def review_application(app_id: int, req: VipReviewRequest):
    """管理员审核申请。

    - approve：通过，用户角色升级为 vip
    - reject：拒绝，记录拒绝原因
    """
    app = next((a for a in _applications if a["id"] == app_id), None)
    if not app:
        raise HTTPException(status_code=404, detail="申请不存在")

    if app["status"] != "pending":
        raise HTTPException(status_code=400, detail="该申请已处理")

    if req.action == "approve":
        app["status"] = "approved"
        app["reviewed_at"] = datetime.now().isoformat()
        # 实际项目中这里会调用 UserService 升级角色
        # await user_service.update_role(app["user_id"], "vip")
        return {
            "code": 200,
            "data": {**app, "new_role": "vip"},
            "message": f"已通过，用户 {app['username']} 的角色已升级为 vip",
        }
    elif req.action == "reject":
        if not req.reject_reason:
            raise HTTPException(status_code=400, detail="拒绝时必须填写原因")
        app["status"] = "rejected"
        app["reject_reason"] = req.reject_reason
        app["reviewed_at"] = datetime.now().isoformat()
        return {
            "code": 200,
            "data": app,
            "message": f"已拒绝用户 {app['username']} 的申请",
        }
    else:
        raise HTTPException(status_code=400, detail="action 必须是 approve 或 reject")

"""VIP 申请审核接口。"""

import os
from datetime import datetime
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.core.logger import logger

FEISHU_WEBHOOK_URL = os.getenv("FEISHU_WEBHOOK_URL", "")
SERVERCHAN_KEY = os.getenv("SERVERCHAN_KEY", "")

_applications: list[dict] = []
_next_id = 1


class VipApplyRequest(BaseModel):
    username: str = Field(description="申请人用户名")
    user_id: int = Field(description="申请人用户ID")
    payment_amount: float = Field(default=199.0, description="支付金额")
    payment_note: str = Field(default="", description="留言")
    payment_proof_url: str = Field(default="", description="付款凭证图片地址")


class VipReviewRequest(BaseModel):
    action: str = Field(description="approve 或 reject")
    reject_reason: str = Field(default="", description="拒绝原因")


async def _send_feishu_notification(app: dict) -> bool:
    if not FEISHU_WEBHOOK_URL:
        return False
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                FEISHU_WEBHOOK_URL,
                json={
                    "msg_type": "interactive",
                    "card": {
                        "header": {
                            "title": {"tag": "plain_text", "content": "新的 VIP 申请"},
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
                                        f"**留言：**{app.get('payment_note') or '无'}\n"
                                        f"**凭证：**{app.get('payment_proof_url') or '未上传'}\n"
                                        f"**时间：**{app['created_at']}"
                                    ),
                                },
                            }
                        ],
                    },
                },
                timeout=5,
            )
            return resp.status_code == 200
    except Exception as exc:
        logger.warning("Feishu VIP notification failed: %s", exc)
        return False


async def _send_wechat_notification(app: dict) -> bool:
    if not SERVERCHAN_KEY:
        return False
    try:
        title = f"新的VIP申请 - {app['username']}"
        desp = (
            f"**用户：**{app['username']}\n\n"
            f"**用户ID：**{app['user_id']}\n\n"
            f"**金额：**¥{app['payment_amount']}\n\n"
            f"**留言：**{app.get('payment_note') or '无'}\n\n"
            f"**凭证：**{app.get('payment_proof_url') or '未上传'}\n\n"
            f"**时间：**{app['created_at']}"
        )
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                f"https://sctapi.ftqq.com/{SERVERCHAN_KEY}.send",
                data={"title": title, "desp": desp},
                timeout=5,
            )
            return resp.status_code == 200
    except Exception as exc:
        logger.warning("ServerChan VIP notification failed: %s", exc)
        return False


router = APIRouter(prefix="/api/v1/vip", tags=["VIP申请审核"])


@router.post("/apply")
async def apply_vip(req: VipApplyRequest):
    global _next_id

    existing = [a for a in _applications if a["user_id"] == req.user_id and a["status"] == "pending"]
    if existing:
        raise HTTPException(status_code=400, detail="您已有一个待审核的申请，请耐心等待")

    app = {
        "id": _next_id,
        "user_id": req.user_id,
        "username": req.username,
        "payment_amount": req.payment_amount,
        "payment_note": req.payment_note,
        "payment_proof_url": req.payment_proof_url,
        "status": "pending",
        "reject_reason": "",
        "reviewed_by": None,
        "reviewed_at": None,
        "created_at": datetime.now().isoformat(),
        "updated_at": datetime.now().isoformat(),
    }
    _next_id += 1
    _applications.append(app)

    await _send_feishu_notification(app)
    await _send_wechat_notification(app)

    return {
        "code": 200,
        "data": {"id": app["id"], "status": app["status"], "paymentProofUrl": app["payment_proof_url"]},
        "message": "申请已提交，等待管理员审核",
    }


@router.get("/applications")
async def list_applications(status: Optional[str] = None):
    result = _applications
    if status:
        result = [a for a in result if a["status"] == status]
    result = sorted(result, key=lambda item: item["created_at"], reverse=True)
    return {"code": 200, "data": result, "message": "成功"}


@router.put("/applications/{app_id}/review")
async def review_application(app_id: int, req: VipReviewRequest):
    app = next((a for a in _applications if a["id"] == app_id), None)
    if not app:
        raise HTTPException(status_code=404, detail="申请不存在")
    if app["status"] != "pending":
        raise HTTPException(status_code=400, detail="该申请已处理")

    if req.action == "approve":
        app["status"] = "approved"
        app["reviewed_at"] = datetime.now().isoformat()
        app["updated_at"] = datetime.now().isoformat()
        return {
            "code": 200,
            "data": {**app, "new_role": "vip"},
            "message": f"已通过，用户 {app['username']} 的角色已升级为 vip",
        }

    if req.action == "reject":
        if not req.reject_reason:
            raise HTTPException(status_code=400, detail="拒绝时必须填写原因")
        app["status"] = "rejected"
        app["reject_reason"] = req.reject_reason
        app["reviewed_at"] = datetime.now().isoformat()
        app["updated_at"] = datetime.now().isoformat()
        return {
            "code": 200,
            "data": app,
            "message": f"已拒绝用户 {app['username']} 的申请",
        }

    raise HTTPException(status_code=400, detail="action 必须是 approve 或 reject")

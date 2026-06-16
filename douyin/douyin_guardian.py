# 抖音群聊守护者 - 自动回复机器人
# 基于 Playwright + OpenClaw AI

import asyncio
import json
import hashlib
import re
import time
import os
import base64
import subprocess
import random
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Set, Tuple
from dataclasses import dataclass, field
from playwright.async_api import async_playwright, Page, ElementHandle
import aiohttp

# ==================== 日志分级 ====================

def log(level: str, msg: str):
    """简单日志分级"""
    timestamp = time.strftime("%H:%M:%S")
    print(f"[{timestamp}] [{level}] {msg}")

def log_error(msg: str): log("ERROR", msg)
def log_warn(msg: str): log("WARN", msg)
def log_info(msg: str): log("INFO", msg)

# ==================== 配置 ====================

@dataclass
class Config:
    """机器人配置"""
    # CDP 连接配置
    cdp_url: str = "http://localhost:9222"

    # OpenClaw CLI 配置(已切换到 CLI 方式,绕过 HTTP API 404 问题)
    model: str = "volcengine-plan/doubao-seed-2.0-pro"
    cli_timeout: int = 60  # CLI 超时时间(秒)

    # 会话ID(用于OpenClaw记忆,运行时按群名动态生成)
    session_id: str = "douyin_guardian_default"

    # 系统提示词
    system_prompt: str = """你是"天庭号"抖音群聊助手,一个活跃、幽默、有态度的AI群友。

【核心人设】
- 身份:群里的"天庭号",不是客服,是群友
- 性格:幽默风趣、偶尔毒舌、爱接梗、会开玩笑
- 风格:口语化、接地气、偶尔用网络流行语

【回复原则】
1. 简短有力:优先20字以内,最多不超过50字
2. 有来有回:像朋友聊天,不是客服回复
3. 会接梗:识别群里的梗,能接就接
4. 不端着:不用"亲""您好"这种客服话术
5. 有态度:该吐槽吐槽,该赞同赞同

【不回复的情况】
- 纯表情、纯数字、纯符号
- 明显是系统消息或广告
- 已经在近期回复过类似内容
- 消息过于简短且无意义

记住:你是群友,不是客服。聊得开心最重要!OpenClaw会记住对话历史,请根据上下文自然回复。"""

    # 白名单群聊(为空则监听所有)
    allowed_groups: List[str] = field(default_factory=lambda: [])

    # 管理员白名单(只有这些用户可以执行控制命令)
    admin_users: List[str] = field(default_factory=lambda: ["正义战士"])

    # 冷却时间(秒)- 已调整为 12 秒,避免打断聊天节奏
    cooldown_user: int = 12
    cooldown_topic: int = 12
    max_delay: int = 15

    # 消息处理
    max_context: int = 20
    min_msg_length: int = 2

    # 目标群配置
    target_group: str = "龙虾探索"

    # 数据持久化
    data_dir: str = "data"
    processed_msgs_file: str = "processed_msgs.json"
    stats_file: str = "stats.json"

# ==================== 数据模型 ====================

@dataclass
class ChatMessage:
    """聊天消息"""
    id: str
    sender: str
    content: str
    timestamp: float
    group_name: str = ""
    is_mentioned: bool = False
    is_self: bool = False
    image_base64: Optional[str] = None  # 图片base64(用于多模态)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "sender": self.sender,
            "content": self.content,
            "timestamp": self.timestamp,
            "group_name": self.group_name,
            "is_mentioned": self.is_mentioned,
            "is_self": self.is_self,
            "has_image": self.image_base64 is not None
        }

@dataclass
class PendingReply:
    """待回复消息"""
    message: ChatMessage
    priority: int
    created_at: float = field(default_factory=time.time)

# ==================== 核心类 ====================

class DouyinGuardian:
    """抖音群聊守护者"""

    BOT_IDENTIFIERS = ["天庭号", "天庭", "tianting", "TT", "tt"]

    def __init__(self, config: Config = None):
        self.config = config or Config()
        self.page: Optional[Page] = None
        self.context: List[Dict] = []
        self.pending_replies: List[PendingReply] = []
        self.processed_msgs: Set[str] = set()
        self.recently_sent: Set[str] = set()
        self.user_cooldown: Dict[str, float] = {}
        self.topic_cooldown: Dict[str, float] = {}
        self.stats = {"total_received": 0, "total_sent": 0, "start_time": time.time()}
        self.running = False
        self.paused = False
        self.last_sender: Optional[str] = None  # 用于连续消息发送者继承
        self.first_run = True  # 首次运行标记,跳过所有历史消息
        self.new_message_queue = []  # 接口拦截到的新消息队列
        self.last_operation_time = time.time()  # 最后操作时间,防检测

        os.makedirs(self.config.data_dir, exist_ok=True)

        self._load_processed_msgs()

    # ========== 持久化 ==========

    def _load_processed_msgs(self):
        """加载已处理消息"""
        filepath = os.path.join(self.config.data_dir, self.config.processed_msgs_file)
        try:
            if os.path.exists(filepath):
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.processed_msgs = set(data.get("hashes", []))
                    self.recently_sent = set(data.get("sent", []))
                    log_info(f"已加载 {len(self.processed_msgs)} 条历史消息记录")
        except Exception as e:
            log_warn(f"加载历史消息失败: {e}")

    def _save_processed_msgs(self):
        """保存已处理消息"""
        filepath = os.path.join(self.config.data_dir, self.config.processed_msgs_file)
        try:
            hashes = list(self.processed_msgs)[-1000:]
            sent = list(self.recently_sent)[-100:]
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump({"hashes": hashes, "sent": sent}, f, ensure_ascii=False)
        except Exception as e:
            log_warn(f"保存消息记录失败: {e}")

    # ========== 初始化 ==========

    async def start(self):
        """启动机器人"""
        log_info("=" * 40)
        log_info("抖音群聊守护者 - 启动中...")
        log_info("=" * 40)

        async with async_playwright() as p:
            try:
                # 连接浏览器,最多重试3次
                browser = None
                for retry in range(3):
                    try:
                        browser = await p.chromium.connect_over_cdp(self.config.cdp_url)
                        break
                    except Exception as e:
                        log_warn(f"连接浏览器失败,重试 {retry+1}/3: {e}")
                        await asyncio.sleep(2)
                if not browser:
                    log_error("连接浏览器失败,请检查9222端口是否开启")
                    return

                # 查找已有的抖音页面(而不是创建新页面)
                log_info("查找抖音页面...")
                found = False
                for ctx in browser.contexts:
                    for pg in ctx.pages:
                        if "douyin.com" in pg.url.lower():
                            self.page = pg
                            log_info(f"已找到抖音页面: {pg.url[:60]}")
                            found = True
                            break
                    if found:
                        break

                if not self.page:
                    log_error("未找到抖音页面,请先打开抖音网页版并登录")
                    return

            except Exception as e:
                log_error(f"连接浏览器失败: {e}")
                log_info("请确保Edge已启动并开启了远程调试: --remote-debugging-port=9222")
                return

            # 连接浏览器后,自动打开目标群聊
            log_info("正在打开目标群聊...")
            await self._open_target_chat()

            self.running = True
            self.start_time = time.time()  # 记录启动时间
            log_info("机器人已启动,开始监听新消息(历史消息已全部跳过)")
            log_info("启动缓冲30秒,期间所有消息都跳过,30秒后开始处理新消息")
            log_info("命令: /暂停 /继续 /状态 /刷新")
            log_info("-" * 40)

            # 首次运行:等5秒加载完所有历史消息,全部强制标记为已处理,不回复
            log_info("正在加载所有历史消息,全部跳过不回复...")
            await asyncio.sleep(5)
            # 多次抓取确保所有历史消息都被标记
            total_skipped = 0
            for i in range(3):
                messages = await self._fetch_messages()
                total_skipped += len(messages)
                for msg in messages:
                    msg.is_self = True  # 强制标记为无需处理的消息
                    await self._handle_message(msg)
                await asyncio.sleep(1)
            self.first_run = False
            log_info(f"已跳过{total_skipped}条历史消息,现在只处理新发送的消息")

            await self._message_loop()

    async def _open_target_chat(self):
        """自动打开目标群聊"""
        target_group = self.config.target_group  # 目标群名(用短名匹配显示不全的群名)

        # 弹窗模式判断:检查是否已经进入具体群聊(能看到消息输入框才是进群了)
        if "chat?isPopup=1" in self.page.url:
            # 检查页面是否有消息输入框,有说明已经在群聊里,直接跳过
            has_input_box = await self.page.locator('div[contenteditable="true"]').count() > 0
            if has_input_box:
                log_info("检测到已进入群聊页面,跳过点击,直接开始监控")
                await asyncio.sleep(2)
                return
            else:
                log_info("检测到弹窗消息列表页,需要点击进入群聊")

        # 等待页面加载完成
        log_info("等待群聊页面加载...")
        await asyncio.sleep(3)

        # 自动点击群聊按钮
        log_info(f"正在点击目标群聊: {target_group}...")
        try:
            # 方法1:用短名模糊匹配CSS选择器
            try:
                lobster_item = self.page.locator('div[class*="conversationConversationItemWrapper"]').filter(has_text='龙虾探索').first
                if await lobster_item.is_visible(timeout=3000):
                    await lobster_item.click(force=True)
                    log_info("已点击群聊(方法:CSS-选择器)")
                    await asyncio.sleep(3)
                    return
            except:
                pass

            # 方法2:用短名模糊匹配文本
            try:
                lobster_item = self.page.locator('text=龙虾探索').first
                if await lobster_item.is_visible(timeout=3000):
                    await lobster_item.click(force=True)
                    log_info("已点击群聊(方法:locator-text)")
                    await asyncio.sleep(3)
                    return
            except:
                pass

            # 方法3:通过 role 和 name 查找
            try:
                chat_item = self.page.get_by_role('listitem').filter(has_text='龙虾').first
                if await chat_item.is_visible(timeout=3000):
                    await chat_item.click(force=True)
                    log_info("已点击群聊(方法:role-listitem)")
                    await asyncio.sleep(3)
                    return
            except:
                pass

            # 方法4:点击第一个可见的聊天项
            try:
                first_chat = self.page.locator('div[class*="ConversationItem"], div[class*="ChatItem"]').first
                if await first_chat.is_visible(timeout=3000):
                    await first_chat.click(force=True)
                    log_info("已点击群聊(方法:first-chat-item)")
                    await asyncio.sleep(3)
                    return
            except:
                pass

            # 兜底方法:JS暴力点击所有包含群名的元素父容器
            try:
                log_info("执行JS暴力点击群聊...")
                await self.page.evaluate('''
                    (function() {
                        // 先点击消息侧边栏,确保群聊列表展开
                        var sidebar = document.querySelector('div[aria-label="消息"]') || document.querySelector('div[class*="sidebar"]');
                        if(sidebar) sidebar.click();

                        // 遍历所有元素找包含群名的
                        var all = document.querySelectorAll('*');
                        for(var i=0; i<all.length; i++) {
                            if(all[i].innerText && all[i].innerText.includes('龙虾探索')) {
                                // 往上找3层父元素点击,直到触发跳转
                                var el = all[i];
                                for(var j=0; j<3; j++) {
                                    if(el) {
                                        el.click();
                                        // 检查是否跳转到聊天页
                                        if(window.location.href.includes('chat')) return;
                                        el = el.parentElement;
                                    }
                                }
                            }
                        }
                    })()
                ''')
                await asyncio.sleep(3)
                # 检查是否成功进入聊天页
                if "chat" in self.page.url:
                    log_info("JS点击成功,已进入群聊页面")
                    return
            except Exception as e:
                log_warn(f"JS点击失败: {e}")

            log_warn("未找到群聊按钮,请手动点击进入群聊")
        except Exception as e:
            log_warn(f"点击群聊失败: {e}")

    # ========== 消息监听 ==========

    async def _message_loop(self):
        """消息监听主循环"""
        check_interval = 2

        while self.running:
            try:
                if not self.paused:
                    await self._process_pending_queue()
                    messages = await self._fetch_messages()

                    for msg in messages:
                        await self._handle_message(msg)

                await self._check_commands()
                await asyncio.sleep(check_interval)

            except Exception as e:
                log_error(f"消息循环异常: {e}")
                await asyncio.sleep(5)

    async def _fetch_messages(self) -> List[ChatMessage]:
        """临时最简方案：直接打印所有页面文本，先抓到消息再说"""
        messages = []
        try:
            # 直接把整个页面的所有文本提取出来，调试用
            all_text = await self.page.evaluate('''() => document.body.innerText''')
            lines = all_text.split('\n')
            # 找包含@天庭号的行
            for line in lines:
                line = line.strip()
                if '@天庭号' in line and len(line) > 5:
                    # 生成消息ID
                    msg_id = hashlib.md5(line.encode()).hexdigest()[:12]
                    if msg_id not in self.processed_msgs:
                        messages.append(ChatMessage(
                            id=msg_id,
                            sender="用户",
                            content=line,
                            timestamp=time.time(),
                            is_mentioned=True,
                            is_self=False
                        ))
                        log_info(f"抓到@消息: {line}")
        except Exception as e:
            log_warn(f"获取消息失败: {str(e)}")
        return messages

    async def _parse_message(self, elem: ElementHandle, index: int) -> Optional[ChatMessage]:
        """解析单个消息元素"""
        try:
            sender_elem = await elem.query_selector('div[class*="MessageBoxMessageTitleavatarName"]')

            if sender_elem:
                sender = (await sender_elem.inner_text()).strip()
                self.last_sender = sender  # 记住发送者
            else:
                sender = self.last_sender or "未知用户"  # 继承上条消息的发送者

            content_elem = await elem.query_selector('div[class*="MessageItemTextcontainer"]')
            content = await content_elem.inner_text() if content_elem else ""
            content = content.strip()

            # 如果既没有文字也没有图片,跳过
            if not content:
                # 检查是否有图片
                img_elem = await elem.query_selector('img[class*="MessageItemImage"], img[class*="image"]')
                if not img_elem:
                    return None
                # 有图片但没文字,设置默认内容
                content = "[图片]"

            # 解析图片(抖音图片可能是base64或URL)
            image_base64 = await self._extract_image(elem)

            is_mentioned = any(id in content for id in self.BOT_IDENTIFIERS)
            is_self = self._is_bot_message(sender, content)

            # 消息去重哈希,简化图片判断,避免重复处理
            hash_content = f"{sender}:{content}"
            # 图片消息只哈希src的前50字符,不用base64避免变化
            if image_base64:
                hash_content += ":[图片]"
            msg_id = hashlib.md5(hash_content.encode()).hexdigest()[:12]

            # 只打印新消息日志,避免刷屏

            return ChatMessage(
                id=msg_id,
                sender=sender,
                content=content,
                timestamp=time.time(),
                is_mentioned=is_mentioned,
                is_self=is_self,
                image_base64=image_base64
            )

        except Exception as e:
            log_warn(f"解析消息失败: {e}")
            return None

    async def _extract_image(self, elem: ElementHandle) -> Optional[str]:
        """临时禁用图片识别,避免卡住"""
        return None

    def _is_bot_message(self, sender: str, content: str) -> bool:
        """检测是否是机器人自己发送的消息"""
        sender_lower = sender.lower()
        if any(id.lower() in sender_lower for id in self.BOT_IDENTIFIERS):
            return True

        if any(id in content for id in self.BOT_IDENTIFIERS[:2]):
            return True

        content_hash = hashlib.md5(content[:30].encode()).hexdigest()[:16]
        if content_hash in self.recently_sent:
            return True

        return False

    # ========== 消息处理 ==========

    async def _handle_message(self, msg: ChatMessage):
        """处理单条消息"""
        if msg.is_self:
            return

        # 已经处理过的消息直接跳过
        if msg.id in self.processed_msgs:
            return

        # 启动后前30秒的所有消息都跳过,只处理之后新发的
        if time.time() - self.start_time < 30:
            self.processed_msgs.add(msg.id)
            return

        # 首次运行:所有历史消息直接标记为已处理,不回复
        if self.first_run:
            self.processed_msgs.add(msg.id)
            return

        # 白名单校验:不在白名单的群直接跳过
        if self.config.allowed_groups:
            current_group = await self._get_current_group()
            if current_group not in self.config.allowed_groups:
                return

        self.processed_msgs.add(msg.id)
        self.stats["total_received"] += 1
        log_info(f"收到新消息: {msg.sender} | {msg.content[:100]}")

        # 交给AI判断是否回复
        pending = PendingReply(message=msg, priority=1 if msg.is_mentioned else 2)
        self.pending_replies.append(pending)

    # ========== 回复处理 ==========

    async def _process_pending_queue(self):
        """处理待回复队列"""
        if not self.pending_replies:
            return

        now = time.time()
        to_remove = []

        for pending in self.pending_replies[:5]:
            if now - pending.created_at > 300:
                to_remove.append(pending)
                continue

            # 检查冷却时间
            if self._is_in_cooldown(pending.message):
                continue

            success = await self._send_reply(pending.message)
            if success:
                to_remove.append(pending)
                self.user_cooldown[pending.message.sender] = now
                topic = self._extract_topic(pending.message.content)
                self.topic_cooldown[topic] = now

        for pending in to_remove:
            if pending in self.pending_replies:
                self.pending_replies.remove(pending)

        self._save_processed_msgs()

    def _is_in_cooldown(self, msg: ChatMessage) -> bool:
        """检查是否在冷却期内"""
        # @机器人的消息直接跳过冷却
        if msg.is_mentioned:
            return False

        now = time.time()

        # 检查用户冷却
        if msg.sender in self.user_cooldown:
            if now - self.user_cooldown[msg.sender] < self.config.cooldown_user:
                return True

        # 检查话题冷却
        topic = self._extract_topic(msg.content)
        if topic in self.topic_cooldown:
            if now - self.topic_cooldown[topic] < self.config.cooldown_topic:
                return True

        return False

    # ========== 工具方法 ==========

    async def _get_current_group(self) -> str:
        """获取当前页面群名,用于多群隔离记忆"""
        try:
            # 抖音群名选择器(可能需要根据实际页面调整)
            selectors = [
                '[class*="chatTitle"]',
                '[class*="groupName"]',
                '[class*="title"]',
                '[class*="ChatTitle"]'
            ]
            for selector in selectors:
                elem = await self.page.query_selector(selector)
                if elem:
                    group_name = (await elem.inner_text()).strip()
                    # 清理特殊字符,用于 session_id
                    group_name = re.sub(r'[^\w\u4e00-\u9fff-]', '_', group_name)
                    if group_name:
                        return group_name
            return "default_group"
        except Exception as e:
            log_warn(f"获取群名失败: {e}")
            return "default_group"

    async def _human_click(self, elem: ElementHandle):
        """模拟人类点击:随机位置、随机延迟,避免风控检测"""
        try:
            box = await elem.bounding_box()
            if box and box['width'] > 0 and box['height'] > 0:
                # 随机点击元素内的任意位置,不要精准点中心
                x = box['x'] + random.randint(5, int(box['width'] - 5))
                y = box['y'] + random.randint(5, int(box['height'] - 5))
                # 随机移动鼠标到位置,模拟人类操作
                await self.page.mouse.move(x, y, steps=random.randint(5, 15))
                await asyncio.sleep(random.uniform(0.05, 0.2))
                # 点击,随机延迟按下和松开时间
                await self.page.mouse.down()
                await asyncio.sleep(random.uniform(0.02, 0.1))
                await self.page.mouse.up()
            else:
                # 兜底:强制点击
                await elem.click(force=True, delay=random.randint(50, 200))
            await asyncio.sleep(random.uniform(0.2, 0.8))
        except Exception as e:
            log_warn(f"模拟点击失败: {e}")
            await elem.click(force=True, delay=random.randint(50, 200))

    def _extract_topic(self, content: str) -> str:
        """提取话题关键词"""
        return content[:10].lower()

    async def _send_reply(self, msg: ChatMessage) -> bool:
        """发送回复(防封版:模拟人类打字操作)"""
        try:
            # ========== 防封1:随机延迟回复,模拟人类思考 ==========
            wait_time = random.uniform(3, 10)
            log_info(f"[防封] 等待{wait_time:.1f}秒后回复,模拟人类思考")
            await asyncio.sleep(wait_time)

            reply_content = await self._generate_reply(msg)

            # 空回复/纯空白内容直接跳过
            if not reply_content or not reply_content.strip():
                return False

            content_hash = hashlib.md5(reply_content[:30].encode()).hexdigest()[:16]
            if content_hash in self.recently_sent:
                log_warn("检测到重复回复内容,跳过")
                return False

            # ========== 防封2:频率限制,1分钟最多回复3条 ==========
            current_minute = int(time.time() / 60)
            if not hasattr(self, 'minute_reply_count'):
                self.minute_reply_count = {}
            if current_minute not in self.minute_reply_count:
                self.minute_reply_count[current_minute] = 0
            if self.minute_reply_count[current_minute] >= 3:
                log_warn("[防封] 1分钟回复超过3条,跳过回复避免风控")
                return False

            # 找到输入框
            input_selectors = [
                'div[contenteditable="true"]',
                'textarea[placeholder*="消息"]',
                'input[placeholder*="消息"]',
                '[class*="input"]',
                '[class*="editor"]'
            ]

            input_elem = None
            for selector in input_selectors:
                try:
                    input_elem = await self.page.wait_for_selector(selector, timeout=2000)
                    if input_elem:
                        break
                except:
                    continue

            if not input_elem:
                log_error("找不到输入框")
                return False

            log_info(f"准备发送回复: {reply_content}")
            # ========== 防封3:模拟人类逐个字符打字,不要一下子填充 ==========
            await input_elem.click()
            await input_elem.fill("")
            await asyncio.sleep(random.uniform(0.3, 0.8))
            for char in reply_content:
                await input_elem.type(char, delay=random.randint(100, 300))
            await asyncio.sleep(random.uniform(0.5, 1.5))
            # 按回车发送
            await input_elem.press("Enter")
            log_info("已发送回复")

            self.stats["total_sent"] += 1
            self.minute_reply_count[current_minute] += 1
            self.recently_sent.add(content_hash)
            log_info(f"发送 -> {msg.sender}: {reply_content[:30]}...")

            return True

        except Exception as e:
            log_error(f"发送失败: {e}")
            return False

    def _call_openclaw_cli(self, session_id: str, prompt: str) -> str:
        """调用 OpenClaw CLI(绕过 HTTP API 404 问题)"""
        try:
            # 简化调用,直接用全局openclaw命令
            # 处理 prompt 中的双引号和特殊字符
            safe_prompt = prompt.replace('"', '\\"').replace('`', '\\`')
            openclaw_cmd = f'openclaw chat --session "{session_id}" --model "{self.config.model}" --prompt "{safe_prompt}"'
            log_info(f"调用OpenClaw: {prompt[:100]}...")

            result = subprocess.run(
                openclaw_cmd,
                shell=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                timeout=120,  # 延长超时到2分钟
                check=False
            )

            if result.returncode != 0:
                log_error(f"OpenClaw CLI 错误: {result.stderr[:200] if result.stderr else '无错误信息'}")
                return ""

            return result.stdout.strip()

        except subprocess.TimeoutExpired:
            log_error("OpenClaw CLI 调用超时")
            return ""
        except Exception as e:
            log_error(f"调用 OpenClaw CLI 失败: {e}")
            return ""

    async def _generate_reply(self, msg: ChatMessage) -> str:
        """生成回复内容 - 调用 OpenClaw CLI(支持多模态)"""
        try:
            # 获取当前群名作为 session_id
            group_name = await self._get_current_group()
            session_id = f"douyin_{group_name}"

            # 构建 prompt
            text_prompt = f"{msg.sender}说: {msg.content}"
            if msg.image_base64:
                text_prompt += "\n(发了一张图片,请识别图片内容后回复)"

            # 临时测试:固定回复,验证发送功能
            if msg.is_mentioned:
                return f"哈喽{msg.sender}!我在的😎"
            return ""

            # # 调用 CLI(同步调用,用 run_in_executor 避免阻塞事件循环)
            # loop = asyncio.get_event_loop()
            # reply = await loop.run_in_executor(
            #     None,
            #     lambda: self._call_openclaw_cli(session_id, text_prompt)
            # )

            # if not reply:
            #     # @机器人的时候兜底回复
            #     if msg.is_mentioned:
            #         return "我脑子卡了,待会再聊~"
            #     return ""

            # 清理可能的回复前缀
            reply = reply.replace("天庭号:", "").replace("天庭号:", "").strip()

            return reply

        except Exception as e:
            log_error(f"生成回复失败: {e}")
            # @机器人的时候兜底回复
            if msg.is_mentioned:
                return "我脑子卡了,待会再聊~"
            return ""

    # ========== 命令处理 ==========

    async def _check_commands(self):
        """检查管理员命令"""
        try:
            # 只检查最新的3条消息
            messages = await self._fetch_messages()
            for msg in messages[-3:]:
                # 只处理管理员的命令
                if msg.sender not in self.config.admin_users:
                    continue
                # 只处理命令消息(以/开头)
                if not msg.content.startswith('/'):
                    continue
                # 避免重复处理命令
                if msg.id in self.processed_msgs:
                    continue
                self.processed_msgs.add(msg.id)
                cmd = msg.content.strip().lower()
                log_info(f"收到管理员命令: {cmd}")
                if cmd == '/暂停':
                    self.pause()
                    # 回复确认
                    await self._send_system_msg("已暂停,收到/继续恢复运行")
                elif cmd == '/继续':
                    self.resume()
                    await self._send_system_msg("已恢复运行")
                elif cmd == '/状态':
                    status = await self.get_status()
                    await self._send_system_msg(status)
                elif cmd == '/刷新':
                    await self.page.reload()
                    await asyncio.sleep(3)
                    await self._open_target_chat()
                    await self._send_system_msg("已刷新页面并重进群聊")
                elif cmd.startswith('/冷却'):
                    try:
                        seconds = int(cmd.split(' ')[1])
                        self.config.cooldown_user = seconds
                        self.config.cooldown_topic = seconds
                        await self._send_system_msg(f"已设置冷却时间为{seconds}秒")
                    except Exception as e:
                        await self._send_system_msg("命令格式错误:/冷却 12")
        except Exception as e:
            log_warn(f"处理命令失败: {e}")

    async def _send_system_msg(self, content: str) -> bool:
        """发送系统通知消息(不经过AI生成)"""
        try:
            input_selectors = [
                'div[contenteditable="true"]',
                'textarea[placeholder*="消息"]',
                'input[placeholder*="消息"]',
                '[class*="input"]',
                '[class*="editor"]'
            ]
            input_elem = None
            for selector in input_selectors:
                try:
                    input_elem = await self.page.wait_for_selector(selector, timeout=2000)
                    if input_elem:
                        break
                except:
                    continue
            if not input_elem:
                log_error("找不到输入框,无法发送系统消息")
                return False
            # 系统消息直接快速输入,不用模拟人类
            await input_elem.fill(content)
            await asyncio.sleep(0.3)
            # 按回车发送
            await input_elem.press("Enter")
            log_info(f"发送系统消息: {content}")
            return True
        except Exception as e:
            log_error(f"发送系统消息失败: {e}")
            return False

    def pause(self):
        """暂停机器人"""
        self.paused = True
        log_info("机器人已暂停")

    def resume(self):
        """恢复机器人"""
        self.paused = False
        log_info("机器人已恢复")

    async def get_status(self) -> str:
        """获取状态"""
        uptime = int(time.time() - self.stats["start_time"])
        current_group = await self._get_current_group()
        return f"""
状态: {'运行中' if self.running else '已停止'} | {'暂停' if self.paused else '活跃'}
运行时间: {uptime//60}分{uptime%60}秒
接收消息: {self.stats["total_received"]}
发送回复: {self.stats["total_sent"]}
待处理: {len(self.pending_replies)}
当前群: {current_group}
会话模式: 多群独立记忆(按群隔离)
"""

    def shutdown(self):
        """优雅关闭"""
        log_info("正在关闭...")
        self.running = False
        self._save_processed_msgs()  # 关闭时保存状态
        log_info("已保存状态,关闭完成")

# ==================== 主程序 ====================

async def main():
    """主函数"""
    config = Config()
    bot = DouyinGuardian(config)

    # 信号处理 - 优雅关闭
    import signal

    def signal_handler(sig, frame):
        log_info("收到中断信号,正在关闭...")
        bot.shutdown()

    signal.signal(signal.SIGINT, signal_handler)

    # 启动
    await bot.start()

if __name__ == "__main__":
    asyncio.run(main())

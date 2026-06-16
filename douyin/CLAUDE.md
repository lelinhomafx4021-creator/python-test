# CLAUDE.md - 抖音群聊守护者项目

## 项目概述
抖音群聊监控机器人"天庭号"，自动检测群聊中的 @提及 并通过 AI（豆包模型）生成智能回复。

## 技术栈
- **语言**: Python 3.x (asyncio)
- **浏览器自动化**: Playwright + CDP 连接
- **AI 模型**: 火山引擎豆包 (doubao-seed-2.0-pro)，通过 OpenClaw CLI 调用
- **启动方式**: PowerShell 脚本

## 核心文件（优先关注）
| 文件 | 作用 |
|------|------|
| `douyin_guardian.py` | 主程序，消息检测和回复逻辑 |
| `douyin_agent.py` | Agent 模块 |
| `start.ps1` / `start_tianting_v3.ps1` | 启动脚本 |
| `tianting_bot_v3.py` | v3 版本机器人 |

## 项目结构要点
- **CDP URL**: `http://localhost:9222`（连接已打开的 Chrome 浏览器）
- **日志文件**: `bot.log`
- **数据目录**: `data/`

## 已知问题与修复记录
- 自我回复问题：通过多重检查发送者名字解决（天庭号、天庭、tianting 等）
- 消息重复处理：processed_msgs 哈希持久化 + recently_sent 集合
- Playwright CDP 方案已废弃，改用 browser-use Agent 方案
- load_state 已禁用（会导致 404 错误）

## 开发规范
- 日志使用分级：log_info / log_warn / log_error
- 配置使用 dataclass（Config 类）
- 异步编程为主（async/await）

## 忽略的文件（不需要分析）
- 所有 backup_*.py / *_backup_*.py 备份文件
- test_*.py / test_*.ps1 测试文件（除非调试需要）
- diff_*.txt 对比文件
- debug_*.py / diagnose_*.py 诊断脚本
- create_*_lnk.ps1 快捷方式创建脚本
- fix_*.py 修复脚本
- *.bat 批处理文件（除 start_all.bat 外）
- .claudeignore 中定义的所有模式

## AI 人设参考
机器人人设为"天庭号"——活跃、幽默、有态度的 AI 群友，不是客服。

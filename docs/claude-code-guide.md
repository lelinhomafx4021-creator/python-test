# Claude Code 使用教程

## 1. 什么是 Claude Code？

Claude Code 是 Anthropic 官方推出的 **AI 编程助手命令行工具**，直接在你终端里运行。它能读/写文件、执行命令、搜索代码、管理 Git，像一个高级搭档一样帮你完成软件工程任务。

**核心能力：**
- 读懂你的整个项目代码库
- 直接修改文件（不是生成代码让你粘贴）
- 执行 shell 命令和脚本
- 管理 Git 仓库（提交、分支、PR）
- 并发执行多个任务

---

## 2. 启动与环境

### 2.1 基本启动

```bash
# 在项目目录下启动
cd your-project
claude

# 跳过权限检查（已配置 auto-allow 时）
claude --dangerously-skip-permissions
```

### 2.2 常用 CLI 参数

| 参数 | 说明 |
|------|------|
| `claude` | 交互式会话模式 |
| `claude "帮我修复这个 bug"` | 一次性问答模式 |
| `claude --model opus` | 指定模型（opus/sonnet/haiku） |
| `claude config` | 打开配置界面 |
| `claude update` | 更新到最新版本 |

---

## 3. 内置命令

命令分两类：**会话内斜杠命令**（`/xxx`）和**CLI 参数**（启动时用）。

### 3.1 会话管理

| 命令 | 功能 | 什么时候用 |
|------|------|-----------|
| `/help` | 查看所有可用命令 | 忘了命令名称时 |
| `/clear` | 清空对话，重新开始 | 话题完全换了，或上下文太乱想重来 |
| `/compact` | 压缩对话历史，释放上下文 | 上下文快满了但话题还没结束，可以加焦点如 `/compact focus on API 改动` |
| `/cost` | 查看当前会话 token 消耗 | 想知道花了多少 token |
| `/doctor` | 诊断 Claude Code 环境 | **排障首选**，功能不正常时先跑这个 |
| `/status` | 查看当前项目状态 | 看工作区、分支、文件变更概况 |
| `/context` | 查看上下文占用详情 | 上下文快满时，看是什么占用了空间 |
| `/stats` | 查看使用统计 | 看历史使用量 |
| `/output-style` | 调整 Claude 输出风格 | 想要更简洁/更详细的回答 |

### 3.2 工作管理

| 命令 | 功能 | 什么时候用 |
|------|------|-----------|
| `/init` | 分析代码库，生成项目的 CLAUDE.md | 新项目第一次用 Claude Code 时 |
| `/review` | Review 一个 Pull Request | 审查代码合入 |
| `/security-review` | 对当前分支做安全检查 | 改完代码合入前 |
| `/simplify` | 审查代码的复用性、质量和效率 | 重构后检查 |
| `/pr-comments` | 查看 PR 评论 | 跟踪 PR review 进展 |
| `/agents` | 配置自定义子代理 | 想定制子代理的行为和工具权限 |
| `/schedule` | 创建定时任务（Routines，云端执行） | 每日 PR review、定期依赖审计等 |
| `/desktop` | 把当前会话交给 Desktop 应用 | 想在 Desktop 端可视化看 diff |
| `/todos` | 查看当前会话任务列表 | 忘了 Claude 正在做的任务清单 |

### 3.3 排障与诊断

这些命令在出问题时非常有用：

| 命令 | 功能 | 什么时候用 |
|------|------|-----------|
| `/doctor` | 全面诊断环境 | **第一步**，检查安装、权限、网络、配置 |
| `/context` | 上下文空间占用图 | 看 CLUADE.md、memory、工具定义各占多少 |
| `/permissions` | 查看当前权限规则 | 工具调用被拦截时排查 |
| `/approved-tools` | 查看已授权的工具 | 确认哪些操作被允许了 |
| `/mcp` | 查看 MCP 服务器和工具 | MCP 工具报错时排查 |
| `/hooks` | 管理 Hooks 配置 | Hook 执行异常时检查 |
| `/status` | 工作区状态概览 | 确认 git 分支、文件变动 |

**典型排障流程：**

```
功能不正常 → /doctor（先跑诊断）
  → 工具被拒 → /permissions 检查权限规则
  → MCP 报错 → /mcp 检查服务端状态
  → 上下文丢失 → /context 看空间占用
  → Hook 失败 → /hooks 检查配置
```

### 3.4 模型切换

| 命令 | 功能 |
|------|------|
| `/model` | 查看和切换模型（opus / sonnet / haiku） |
| `/fast` | 切换快速模式（仅 Opus 4.6+） |

也可以在启动时指定：`claude --model opus`

### 3.5 CLI 启动参数

| 参数 | 功能 | 示例 |
|------|------|------|
| `claude` | 交互式会话 | `claude` |
| `claude "任务描述"` | 一次性执行，完成后退出（`-p` 简写） | `claude "修复 auth bug"` |
| `claude -p "任务"` | Pipe 模式，适合脚本/管道 | `tail log \| claude -p "分析异常"` |
| `claude --model <name>` | 指定模型 | `claude --model opus` |
| `claude --resume` | 恢复上次会话 | `claude --resume` |
| `claude --continue` | 继续最新会话 | `claude --continue` |
| `claude --fork-session` | 从上次会话分叉 | `claude --fork-session` |
| `claude --teleport` | 从 Web 端拉回会话 | `claude --teleport` |
| `claude config` | 打开配置面板 | `claude config` |
| `claude update` | 更新 Claude Code | `claude update` |
| `claude --version` | 查看版本号 | `claude --version` |
| `claude --add-dir <path>` | 添加额外工作目录 | `claude --add-dir ~/other-project` |

### 3.6 配置相关

| 命令 | 功能 |
|------|------|
| `/config` | 打开配置面板（主题 / 模型 / 权限 / env 等） |
| `/add-dir` | 添加额外工作目录给 Claude 访问 |
| `/permissions` | 查看和调整权限规则 |
| `/setup` | 初次安装向导 |
| `/loop` | 定时重复执行某个任务（如 `/loop 5m /test`） |
| `/hooks` | 管理 Hooks 配置 |
| `/ide` | IDE 集成相关设置 |
| `/terminal-setup` | 终端集成设置 |
| `/login` / `/logout` | 登录/登出 Anthropic 账号 |

### 3.7 插件与扩展

| 命令 | 功能 |
|------|------|
| `/plugin` | 浏览插件市场 |
| `/plugin install <name>` | 安装插件 |
| `/mcp` | 查看已连接的 MCP 服务器 |
| `/memory` | 打开持久化记忆编辑器 |

### 3.8 版本与更新

```bash
# 查看当前版本
claude --version

# 手动更新
claude update

# 查看更新日志（在 Claude Code 会话中直接问）
"Claude Code 最近更新了什么？"
"What's new in Claude Code?"
```

> **注意：** 如果 `autoUpdatesChannel` 设为 `latest`（在 `settings.json` 中），Claude Code 会**自动后台更新**。如果某功能突然不工作了，可能是自动更新引入了行为变化。排查时先 `/doctor`，再 `claude --version` 确认版本。

---

## 4. 核心工作流

### 4.1 让 Claude 读代码

```bash
# 直接提问
"这个文件是做什么的？"
"找出所有 API 接口定义"
"解释 auth middleware 的逻辑"
```

Claude 会使用 Glob（文件匹配）和 Grep（内容搜索）来定位相关代码。

### 4.2 让 Claude 改代码

```bash
# 明确指定要做什么
"把 calculateTotal 函数改成 async 的"
"在 User 模型里新增一个 email 字段"
"重构 login 接口，改用 JWT 认证"
```

Claude 会用 Edit 工具做精确替换，而不是重写整个文件。

### 4.3 让 Claude 执行命令

```bash
# 运行测试、构建、git 等
"帮我把这个分支推送到远程"
"跑一下所有测试"
"安装 axios 依赖"
```

---

## 5. CLAUDE.md — 项目"说明书"

**CLAUDE.md** 是你项目根目录下的一个 markdown 文件，Claude 每次启动都会自动读取。它定义了 Claude 在这个项目中的行为准则。

### 5.1 为什么需要 CLAUDE.md？

默认情况下，Claude 对你的项目一无所知。CLAUDE.md 告诉它：
- 项目的技术栈和目录结构
- 编码规范和风格偏好
- 协作方式（比如要不要问你确认）
- 常见陷阱和注意事项

### 5.2 我们项目的 CLAUDE.md 示例

```markdown
# AI-Investor 项目协作规则

## 用户身份
- 用户是学生/学习者，正在通过这个项目学习技术
- **教学优先于执行**：不要只埋头干活，每一步都要解释原理和为什么

## 协作方式
- 每次修改代码时，解释**为什么这么做**
- 遇到新概念、新技术、新工具时，主动科普背景知识
- 如果有多种实现方式，简要说明各自的优缺点和适用场景
- 代码注释可以省略，但口头讲解不能省

## 项目结构
- `aipy2/` — Python 后端 (FastAPI)
- `frontend/` — Vue 3 前端
- `java-ai-gateway/` — Java 网关 (Spring Boot)
```

有了这些规则，Claude 就会用"教学风格"和你互动，而不是机械地完成任务。

### 5.3 使用 `/init` 自动生成

```bash
# 在 Claude Code 会话中
/init
```

它会分析你的代码库并生成一份初始的 CLAUDE.md。

---

## 6. 记忆系统 — 让 Claude "记住"你

Claude Code 有一个**持久化记忆系统**，信息会保存下来，跨会话保留。

### 6.1 记忆类型

| 类型 | 用途 | 示例 |
|------|------|------|
| `user` | 关于你的身份、偏好、知识水平 | "用户是后端新手，前端经验丰富" |
| `feedback` | 你的行为偏好：做这个、别做那个 | "不要 mock 数据库" |
| `project` | 项目当前状态、目标、截止日期 | "auth 重构必须在周五前完成" |
| `reference` | 外部系统的指针 | "Bug 追踪在 Linear 项目 INGEST" |

### 6.2 什么时候 Claude 会自动记录？

- 你纠正了它的做法（"不对，你应该..."）
- 你明确确认了一个不常见的做法（"对，就这样"）
- 你告诉它关于你自己的背景信息
- 你提到了项目的目标、截止日期、外部资源

### 6.3 手动管理记忆

```bash
# 查看记忆
/memory

# 直接说
"记住：我不喜欢用 any 类型"
"记住：推送前一定先问过我"
```

---

## 7. 工具权限体系

### 7.1 三种权限模式

每个工具操作都需要权限：

| 模式 | 行为 |
|------|------|
| **Always Allow（始终允许）** | 自动执行，不询问 |
| **Ask（询问）** | 每次弹出确认对话框 |
| **Deny（拒绝）** | 始终拒绝 |

### 7.2 管理权限

权限存储在 `.claude/settings.json`（项目级）或 `~/.claude/settings.json`（用户级）。

```bash
# 查看当前权限
claude config

# 在会话中直接调整
"允许自动执行 npm 命令"
"把 git push 的权限改成问我确认"
```

### 7.3 文件匹配规则

```json
{
  "permissions": {
    "allow": [
      "Bash(npm run *)",        // 允许所有 npm run 命令
      "Bash(git: *)",           // 允许所有 git 操作
      "Read(/home/user/**)",    // 允许读该目录下所有文件
      "Edit(src/**)"            // 允许编辑 src 下所有文件
    ],
    "deny": [
      "Bash(rm: *)"             // 禁止所有删除命令
    ]
  }
}
```

---

## 8. 全部内置工具一览

Claude Code 的内置工具远不止文件编辑。以下是官方完整列表（v2.1.x）：

### 8.1 文件操作

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `Read` | 读取文件内容（含图片、PDF） | 否 |
| `Write` | 创建或覆盖文件 | 是 |
| `Edit` | 精确字符串替换编辑 | 是 |
| `Glob` | 按模式匹配查找文件 | 否 |
| `Grep` | 正则表达式搜索文件内容 | 否 |
| `NotebookEdit` | 修改 Jupyter 笔记本 | 是 |

### 8.2 执行与 Shell

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `Bash` | 执行 Unix shell 命令 | 是 |
| `PowerShell` | 原生执行 PowerShell 命令 | 是 |

> **PowerShell 工具：** Windows 上可替代 Git Bash，需设置 `CLAUDE_CODE_USE_POWERSHELL_TOOL=1`

### 8.3 Web 联网

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `WebSearch` | 网页搜索，返回结果链接和摘要 | 是 |
| `WebFetch` | 抓取指定 URL 内容并处理 | 是 |

### 8.4 任务与计划

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `TodoWrite` | 管理会话任务清单 | 否 |
| `TaskCreate` / `TaskGet` / `TaskList` / `TaskUpdate` / `TaskStop` | 精细任务管理 | 否 |
| `EnterPlanMode` / `ExitPlanMode` | 进入/退出规划模式 | 否/是 |

### 8.5 调度与监控

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `CronCreate` / `CronDelete` / `CronList` | 会话内定时任务（类似 cron） | 否 |
| `Monitor` | 后台运行命令并逐行反馈输出（v2.1.98+） | 是 |

> **Monitor：** 让 Claude "盯着"日志、CI 状态、文件变化，有新内容时主动告知你，不阻塞对话。

### 8.6 代码智能（LSP）

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `LSP` | 跳转定义、查找引用、类型信息、诊断错误 | 否 |

> **LSP：** 安装对应语言的 [代码智能插件](/en/discover-plugins#code-intelligence) 后激活，Claude 每次编辑文件后自动检测类型错误。

### 8.7 代理与协作

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `Agent` | 派生子代理执行独立任务 | 否 |
| `Skill` | 调用可复用的工作流技能 | 是 |
| `AskUserQuestion` | 多选问题向用户确认 | 否 |
| `TeamCreate` / `TeamDelete` / `SendMessage` | 多代理团队协作（实验性） | 否 |

### 8.8 MCP 外部工具

| 工具 | 功能 | 需要权限 |
|------|------|---------|
| `ListMcpResourcesTool` / `ReadMcpResourceTool` | 列出/读取 MCP 服务器暴露的资源 | 否 |
| `ToolSearch` | 按需搜索和加载 MCP 工具 | 否 |

---

## 9. Agents（子代理）

Claude 可以**派生子代理**来处理复杂任务，类似于分配工作给专门的人。

### 9.1 代理类型

| 代理 | 用途 |
|------|------|
| `general-purpose` | 通用代理，做复杂研究或执行多步骤操作 |
| `Explore` | 快速搜索代理，在代码库中定位文件/符号 |
| `Plan` | 架构设计代理，在做大改动前先出方案 |
| `code-reviewer` | 代码审查代理，独立审查改动 |
| `claude-code-guide` | 回答 Claude Code 使用问题 |

### 9.2 Agent Teams（实验性）

多个子代理可以组成**团队**协同工作：一个 Leader 代理分配子任务，多个 Teammate 代理并行执行，最后由 Leader 汇总结果。需设置 `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` 开启。

### 9.3 使用场景

```bash
# 当你需要广泛探索代码时
"搜索所有使用了 JWT 的地方"
→ Claude 会派 Explore 代理去搜

# 当你需要规划大改动时
"我想重构用户认证系统"
→ Claude 会先进 Plan 模式，设计好方案再动手
```

**为什么要了解这个？** 因为 Claude 有时会跟你说"让我派一个代理去搜索"——这意味着它在并行执行，效率更高。

---

## 10. Plan 模式（规划模式）

在做**重大改动**（影响多个文件、涉及架构决策）之前，Claude 会进入 Plan 模式：

1. **探索**：读懂现有代码结构
2. **设计**：提出实现方案，列出受影响的文件
3. **确认**：让你审阅方案并获得批准
4. **执行**：按计划逐步实现

**什么时候会触发？**
- 新增功能
- 重构代码
- 涉及多个文件的改动
- 有多种实现方式需要权衡

---

## 11. 实用技巧

### 11.1 写好提示词

```
❌ "修 bug"                    → 太模糊
✅ "登录接口返回 500 错误，帮我排查"   → 清楚

❌ "优化代码"                    → 太模糊
✅ "分析 Dashboard.vue 的性能瓶颈" → 清楚

❌ "加一个功能"                   → 太模糊
✅ "在 user 表加一个 phone 字段，对应的 API 和前端都要改" → 清楚
```

### 11.2 利用上下文

- Claude 能"看到"你 IDE 中打开的文件
- Claude 能"看到"你选中的代码
- 你可以直接引用文件路径或行号

### 11.3 渐进式开发

```
第1轮："帮我设计 user 认证的数据模型"
第2轮："好，现在实现注册 API"
第3轮："加上 JWT 令牌生成逻辑"
第4轮："在路由上加认证中间件"
```

每次一个小步骤，Claude 的前后文不会丢失，你也能跟上每一步。

### 11.4 回退与恢复

```bash
# 如果不满意改动
git diff                          # 查看改动
git checkout -- <file>            # 回退单个文件
git stash                         # 暂存所有改动

# 或者使用 GitLens / IDE 的本地历史功能
```

### 11.5 快捷键（VS Code 扩展）

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+Shift+L` | 打开 Claude Code 面板 |
| 选中代码 + 右键 | 让 Claude 解释/改进选中的代码 |

### 11.6 多端运行

Claude Code 不仅能在终端运行，同一套配置（CLAUDE.md、settings.json、MCP）可以跨端使用：

| 端 | 适用场景 |
|-----|---------|
| **Terminal CLI** | 日常主力，完整功能 |
| **VS Code 扩展** | 行内 diff 预览、@-mention、Plan 审查 |
| **JetBrains 插件** | IntelliJ / PyCharm / WebStorm 内的交互式 diff |
| **Desktop 应用** | 可视化 diff 审查、多会话并排、定时任务 |
| **Web（claude.ai/code）** | 云端执行，不需要本地环境，可同时跑多个任务 |
| **iOS App** | 移动端继续会话 |

```bash
# 把当前终端会话交给 Desktop 应用（可视化审查 diff）
/desktop

# 从 Web 拉回会话到本地终端
claude --teleport
```

### 11.7 Monitor — 让 Claude 帮你"盯着"

```bash
# Claude 后台监控日志，发现问题时主动告诉你
"帮我看一下 app.log，有错误就提醒我"

# 监控 PR/CI 状态
"盯着这个 PR，合入后告诉我"
```

Monitor 是后台运行的观察者，不阻塞你继续工作。需要 v2.1.98+。

### 11.8 LSP 代码智能

安装对应语言的 [代码智能插件](https://code.claude.com/docs/en/discover-plugins#code-intelligence) 后，Claude 能：
- 每次编辑后**自动检测类型错误**，无需手动 build
- 跳转到符号定义、查找引用、追踪调用链
- 查看类型信息

这让你在 TypeScript、Python、Rust、Go 等语言中获得类似 IDE 的实时检查能力。

---

## 12. 常见问题

### Q: Claude Code 有哪些运行环境？

Claude Code 支持三个执行环境：

| 环境 | 代码在哪跑 | 适用场景 |
|------|----------|---------|
| **Local（本地）** | 你的机器 | 默认模式，完整文件/工具权限 |
| **Cloud（云端）** | Anthropic 管理的虚拟机 | 卸载长时间任务，处理不在本地的仓库 |
| **Remote Control** | 你的机器，浏览器控制 | 离开工位时手机/平板继续工作 |

### Q: 上下文窗口是什么？为什么会满？

Claude 的"记忆"有长度限制（上下文窗口）。对话太长时，最早的内容会被"遗忘"。如果你的会话变得很长：

- 用 `/compact` 压缩历史
- 用 `/clear` 开始新会话
- 避免让 Claude 读入整个大文件（让它读关键部分就行）

### Q: 为什么 Claude 不直接执行命令？

权限设置问题。检查 `.claude/settings.json`，确认相应操作没有被设为 `deny`。

### Q: Claude 能联网吗？

可以，有两个联网工具：

| 工具 | 功能 | 场景 |
|------|------|------|
| **WebSearch** | 搜索网页，返回结果链接和摘要 | 查最新文档、找解决方案 |
| **WebFetch** | 抓取指定 URL 的完整内容 | 读取某个具体网页正文 |

**使用方式：** 不需要自己打开浏览器，直接说"帮我搜 XXX"或"读一下这个页面"，Claude 会自动调用对应工具。

**限制：**
- WebFetch 受 Anthropic 服务端安全策略限制，某些域名可能被拦截（与你本地网络无关）
- WebSearch 依赖底层模型兼容性 — 如果用非 Anthropic 模型（如 DeepSeek）作为后端，可能因参数不兼容而失败
- 遇到访问不了的页面，可以尝试开代理（会影响服务器出口流量，不是你本地流量）

### Q: WebSearch 报错 "does not support this tool_choice" 怎么办？

这个错误说明底层模型不兼容 Claude Code 的 WebSearch 实现。WebSearch 内部会向模型 API 发送 `tool_choice` 参数，如果用的是第三方兼容接口（如 DeepSeek 的 Anthropic 兼容端点），可能不完全支持这个参数。

**排查步骤：**

```bash
# 1. 先确认版本和模型
claude --version
# 在会话中问："我用的是什么模型？"

# 2. 跑诊断
/doctor

# 3. 看配置
/context          # 看上下文占用
cat ~/.claude/settings.json   # 查模型和 base_url 配置
```

**常见原因：**
- 设置里 `ANTHROPIC_BASE_URL` 指向了第三方兼容接口，该接口未完全实现 Anthropic API
- Claude Code 自动更新后 WebSearch 实现变了，旧接口不兼容
- 第三方接口在不同模型上的 `tool_choice` 支持度不同

**解决方案：**
- 换用 Anthropic 原生 API
- 降级 Claude Code（如果确认是新版本引入的问题）
- 等待第三方接口更新支持

### Q: 某功能突然不工作了怎么排查？

先确认是不是 Claude Code 自动更新导致的。你的 `autoUpdatesChannel` 如果在 `settings.json` 中设为 `latest`，CC 会**静默自动升级**。

**标准排障流程：**

```
1. /doctor          → 全面诊断环境
2. claude --version → 确认版本号（对比问题出现前后的版本）
3. /permissions     → 检查权限规则有没有变化
4. /context         → 看上下文是否被挤占
5. /mcp             → 检查 MCP 服务端
```

**版本回退（如果确认是新版引入的问题）：**

```bash
# 安装指定版本
npm install -g @anthropic-ai/claude-code@<version>
```

**第三方后端排查：** 如果你用了 DeepSeek 等第三方 API 兼容接口（`ANTHROPIC_BASE_URL` 指向非 Anthropic 地址），问题很可能是兼容性缺口。先在设置里切回 Anthropic 原生 API 测试，看功能是否恢复正常。

### Q: Monitor 是什么？什么时候用？

Monitor 是 v2.1.98 新增的**后台监控工具**。Claude 在后台运行你指定的命令，把每一行输出逐条反馈给自己，有新内容时主动提醒你。

```bash
"盯着这个日志文件，有 ERROR 就告诉我"
"监控 PR #42 的状态，合并后提醒我"
"监听 src/ 目录的改动，汇总成 changelog"
```

它不阻塞对话——你可以继续和 Claude 聊，同时 Claude 在后台"盯着"。用完让 Claude 停掉就行。

### Q: LSP 代码智能怎么用？

Claude Code 内置了 LSP（Language Server Protocol）支持。安装对应的代码智能插件后：
- 每次编辑文件后**自动检测类型错误**（无需手动 build）
- 可以跳转到定义、查找引用、查看类型信息
- 类似 IDE 的实时检查能力

需要你在插件市场安装对应语言的插件（TypeScript、Python、Rust、Go 等），再安装语言服务端二进制文件。

### Q: 改动错了怎么办？

用 `git diff` 查看改动，`git checkout` 回退。Claude 的所有文件修改都在你的 Git 工作区中，随时可撤销。

---

## 13. 下一步

1. **多练习**：最好的学习方式是多用。遇到小任务就让 Claude 试试。
2. **看它怎么做**：注意 Claude 用什么工具、怎么拆解任务——你在学习编程的同时也在学习 AI 协作。
3. **完善 CLAUDE.md**：随着你对项目的理解加深，持续更新项目说明书。
4. **提反馈**：Claude 做得好或不好都告诉它，它会记住并改进。

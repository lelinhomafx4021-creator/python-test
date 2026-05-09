# Git 基础教程

> 你目前会的：连接远程仓库 + push。这篇教程补齐你日常开发需要的其他核心操作。

## 一、Git 和 GitHub 的区别

很多人搞混这两个：

| | Git | GitHub |
|---|---|---|
| 是什么 | 一个**本地工具**，安装在你电脑上 | 一个**网站/平台**，代码托管在云端 |
| 做什么 | 记录文件的每次修改，管理版本 | 存放代码仓库，提供 PR、Issues、CI/CD |
| 离线能用吗 | 能，所有操作都在本地 | 不能，需要联网 |

简单理解：**Git 是发动机，GitHub 是停车场。** 你用 Git 在本地管理代码，用 GitHub 把代码分享给别人。

---

## 二、Git 的三个区域

这是理解 Git 最关键的概念：

```
工作区（Working Directory）  →  暂存区（Staging Area）  →  仓库（Repository）
      你编辑的文件              git add                  git commit
```

- **工作区**：你正在编辑的文件，就是你在 VS Code 里看到的
- **暂存区**：你准备好要提交的修改（`git add` 后进入这个区域）
- **仓库**：最终的历史记录（`git commit` 后保存到这里）

**为什么要分三步？** 因为你可能改了 10 个文件，但只想提交其中 3 个。`git add` 让你选择性地暂存，而不是一股脑全提交。

---

## 三、基本工作流

### 1. 查看状态

```bash
git status
```

这是你最常用的命令，随时看看当前状态：
- 哪些文件被修改了（红色）
- 哪些文件已暂存（绿色）
- 有没有未追踪的新文件

### 2. 添加到暂存区

```bash
git add 文件名              # 添加单个文件
git add src/main.py        # 添加指定文件
git add src/               # 添加整个目录
git add .                  # 添加所有修改（最常用）
```

### 3. 提交到仓库

```bash
git commit -m "描述你改了什么"
```

commit message 的写法约定：

```bash
git commit -m "feat: 新增用户登录功能"       # 新功能
git commit -m "fix: 修复登录页面崩溃"         # 修 bug
git commit -m "docs: 更新 README"             # 改文档
git commit -m "refactor: 重构登录逻辑"         # 重构
git commit -m "chore: 清理无用文件"            # 杂务
```

**不需要每次都 add + commit**，可以合并：

```bash
git add . && git commit -m "feat: xxx"
```

### 4. 推送到远程

```bash
git push                    # 推送到当前分支
git push origin master      # 推送到指定远程分支
```

---

## 四、分支（Branch）

分支是 Git 最重要的功能，没有之一。

### 为什么需要分支

假设你正在开发新功能，写到一半发现线上有 bug 需要紧急修复。如果没有分支，你要么：
- 把写了一半的代码提交到 master（不干净）
- 手动备份文件再改（太原始）

有了分支，你可以：
- 从 master 创建一个"修复分支"，修完 bug 合回去
- 再切回"功能分支"继续开发

**分支就是平行宇宙**，互不影响。

### 分支操作

```bash
# 查看所有分支（* 表示当前分支）
git branch

# 创建新分支
git branch feature/login

# 切换分支
git checkout feature/login
# 或者新版写法
git switch feature/login

# 创建并切换（一步到位）
git checkout -b feature/login
git switch -c feature/login    # 等价写法

# 删除分支
git branch -d feature/login    # 删除已合并的分支
git branch -D feature/login    # 强制删除
```

### 分支实战流程

```
master（主线）
  │
  ├── git checkout -b feature/login
  │     │
  │     │  （在这里开发新功能）
  │     │
  │     ├── git add . && git commit -m "feat: login page"
  │     ├── git add . && git commit -m "feat: login api"
  │     │
  │     └── git checkout master
  │           │
  │           └── git merge feature/login   （合并回主线）
  │
  └── 继续开发...
```

### 分支命名约定

```bash
feature/login        # 新功能
fix/login-crash      # 修复 bug
hotfix/server-down   # 紧急修复
docs/api-guide       # 文档更新
refactor/user-service # 重构
```

---

## 五、合并分支

```bash
# 1. 先切换到目标分支（你想把代码合并到哪里）
git checkout master

# 2. 合并功能分支
git merge feature/login
```

### 合并冲突

两个人改了同一个文件的同一行，Git 不知道该用谁的，就会产生冲突。

冲突长这样：

```
<<<<<<< HEAD
这是你的修改
=======
这是别人的修改
>>>>>>> feature/login
```

**解决方法：**
1. 打开冲突文件，手动选择保留哪部分（删掉 `<<<<<<<`、`=======`、`>>>>>>>` 标记）
2. `git add 冲突文件`
3. `git commit`

**避免冲突的习惯：** 开发前先 `git pull` 拉取最新代码，开发完尽早 push 和合并。

---

## 六、查看历史

```bash
# 查看提交历史
git log

# 简洁模式（一行显示一个提交）
git log --oneline

# 图形化显示分支关系
git log --oneline --graph --all

# 查看某次提交改了什么
git show abc1234

# 查看某个文件的历史
git log --oneline src/main.py
```

`git log --oneline --graph --all` 的效果：

```
* abc1234 (HEAD -> master) merge feature/login
|\
| * def5678 feat: login api
| * ghi9012 feat: login page
|/
* jkl3456 init project
```

---

## 七、撤销和回退

### 撤销工作区的修改（还没 add）

```bash
git checkout -- 文件名        # 丢弃修改，恢复到上次提交的版本
git restore 文件名            # 新版写法，效果一样
```

### 撤销暂存（add 了但没 commit）

```bash
git reset HEAD 文件名          # 从暂存区移出，修改还保留
git restore --staged 文件名    # 新版写法
```

### 修改最后一次 commit

```bash
git commit --amend -m "新的提交信息"
```

### 回退到某个版本（危险操作）

```bash
# 回退到某次提交，保留修改
git reset --soft abc1234

# 回退到某次提交，丢弃修改（危险！）
git reset --hard abc1234
```

**`--soft` vs `--hard`：**
- `--soft`：代码回退了，但修改还在暂存区（安全）
- `--hard`：代码和修改全部回退（危险，改了的东西就没了）

---

## 八、远程仓库操作

```bash
# 查看远程仓库地址
git remote -v

# 拉取远程更新（不合并）
git fetch

# 拉取并合并（日常最常用）
git pull
git pull origin master

# 推送到远程
git push
git push origin master

# 首次推送并设置上游分支
git push -u origin master
# 之后直接 git push 就行了
```

### pull vs fetch

```
git fetch    →  只下载，不合并（安全，可以先看看再决定）
git pull     =  fetch + merge（一步到位，但可能有冲突）
```

**建议：** 团队协作时用 `fetch` + 手动合并更安全；个人项目直接 `pull` 就行。

---

## 九、 .gitignore

有些文件不应该提交到 git，比如：
- 编译产物（`target/`、`node_modules/`、`__pycache__/`）
- 配置文件里的密码（`.env`）
- IDE 配置（`.idea/`、`.vscode/`）
- 日志文件（`logs/`）

在项目根目录创建 `.gitignore` 文件：

```text
# Python
__pycache__/
*.pyc
.venv/
.env

# Java
target/
*.class
*.jar

# Node
node_modules/
dist/

# IDE
.idea/
.vscode/
*.swp

# 日志
logs/
*.log

# 系统文件
.DS_Store
Thumbs.db
```

**注意：** 如果文件已经被 git 追踪了，加到 `.gitignore` 也不会生效。需要先取消追踪：

```bash
git rm --cached 文件名
```

---

## 十、常用命令速查表

| 操作 | 命令 |
|------|------|
| 查看状态 | `git status` |
| 添加到暂存区 | `git add .` |
| 提交 | `git commit -m "msg"` |
| 推送 | `git push` |
| 拉取 | `git pull` |
| 查看历史 | `git log --oneline` |
| 创建分支 | `git branch 分支名` |
| 切换分支 | `git switch 分支名` |
| 创建并切换 | `git switch -c 分支名` |
| 合并分支 | `git merge 分支名` |
| 删除分支 | `git branch -d 分支名` |
| 撤销修改 | `git restore 文件名` |
| 撤销暂存 | `git restore --staged 文件名` |
| 修改上次提交 | `git commit --amend` |
| 查看差异 | `git diff` |
| 查看分支图 | `git log --oneline --graph --all` |

---

## 十一、你的项目实战

拿你的 ai-investor 项目举例，一个典型的开发流程：

```bash
# 1. 开始新功能前，先确保 master 是最新的
git checkout master
git pull

# 2. 创建功能分支
git checkout -b feature/add-user-test

# 3. 开发、测试...
# 编写代码...

# 4. 提交
git add .
git commit -m "feat: 添加用户模块单元测试"

# 5. 推送到远程
git push -u origin feature/add-user-test

# 6. 去 GitHub 上创建 Pull Request，请求合并到 master
# （在网页上操作，或用 gh pr create）

# 7. 代码审查通过后，合并
git checkout master
git merge feature/add-user-test
git push

# 8. 删除已合并的功能分支
git branch -d feature/add-user-test
```

---

## 十二、常见问题

### Q：commit 了但忘了 add 某个文件

```bash
git add 遗漏的文件
git commit --amend --no-edit    # 追加到上次 commit，不改提交信息
```

### Q：push 了但发现 commit 信息写错了

```bash
git commit --amend -m "正确的信息"
git push --force-with-lease     # 强制推送（只在自己的分支上用！）
```

### Q：代码改乱了想回到某个版本

```bash
git log --oneline               # 找到想回到的 commit hash
git reset --soft abc1234        # 安全回退，代码还在
```

### Q：两个分支都改了同一个文件

先合并，有冲突就手动解决（编辑文件，删除冲突标记），然后 `git add` + `git commit`。

---

> 下一篇：[03 - Docker 基础](./03-docker.md)（待写）

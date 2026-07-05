# Frontend Workspace

AI Investor 的前端工作台，基于 `Vue 3 + TypeScript + Vite`。

## 页面范围

- 登录与注册
- 会员主页与个人资料
- 行情、板块、热点新闻
- 自选股管理
- 模拟交易与资产快照
- AI 投研聊天
- 人工工单与管理后台

## 开发命令

```bash
npm ci
npm run dev
```

生产构建：

```bash
npm run build
```

代码检查：

```bash
npm run lint
```

## 目录约定

- `src/views`：页面级视图
- `src/components`：复用组件
- `src/api`：接口与状态编排
- `src/composables`：组合式逻辑
- `src/utils`：格式化、图表加载等工具

## 当前实现重点

- 使用 fetch-based SSE 处理流式 AI 对话
- 工作台与管理台共用一套登录态
- 行情、交易、聊天、工单统一在终端式界面中串联

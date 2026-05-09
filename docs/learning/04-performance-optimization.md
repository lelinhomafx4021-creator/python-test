# 性能优化实战教学

> 本篇记录 AI-Investor 项目中发现并修复的 5 类性能问题。
> 每个问题都按 **现象 → 原因 → 修复 → 原理** 的结构讲解。

---

## 一、Python 线程池重复创建

### 现象

`news_tool.py` 中的 `_run_with_timeout` 函数，每次被调用都会新建一个 `ThreadPoolExecutor`：

```python
def _run_with_timeout(task_name, supplier):
    with ThreadPoolExecutor(max_workers=1) as executor:  # ← 每次都 new
        future = executor.submit(supplier)
        return future.result(timeout=8)
```

而 `collect_hot_news` 会调用它两次（财新 + 东方财富），所以每次抓新闻都会创建并销毁两个线程池。

### 为什么这是问题

线程池的创建涉及操作系统级别的资源分配：

1. **`pthread_create` 系统调用**：每创建一个线程，OS 需要分配栈空间（默认 8MB）、内核数据结构
2. **线程池初始化**：内部要创建任务队列、工作线程、同步锁
3. **销毁开销**：`shutdown` 要等所有任务完成，释放资源

对于一个可能每秒被调用多次的接口，这个开销是不可忽视的。

### 修复

```python
# 模块级单例，进程生命周期内只创建一次
_news_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="news-fetch")

def _run_with_timeout(task_name, supplier):
    future = _news_executor.submit(supplier)  # ← 复用同一个池子
    try:
        return future.result(timeout=8)
    except FutureTimeoutError:
        logger.warning("抓取超时: %s", task_name)
    return None
```

### 核心原理

> **可复用的重量级资源，提为模块级常量。**

类似的东西还有：
- 数据库连接池（HikariCP、Druid）
- Redis 连接池
- HTTP 客户端（`httpx.AsyncClient`）
- JSON 序列化器（`ObjectMapper`）

它们的共同特点：创建昂贵，复用便宜。

---

## 二、N+1 查询问题

### 现象

`MarketService.listStocks` 加载股票列表时，对每只股票都执行：

```java
for (MarketStockListItemVO item : stockPage.getItems()) {
    cacheAndPersistQuote(quote);   // 1 次 Redis 写 + 1 次 DB upsert
    ensureStockRecord(quote);      // 1 次 DB 查询 + 可能的 insert/update
}
```

一页 40 只股票 = **80~160 次数据库/Redis 操作**。

### 为什么这是问题

数据库操作的耗时主要花在**网络往返**上：

```
应用服务器 ←→ 数据库服务器
   |              |
   |--- 发送 SQL -->|
   |<-- 返回结果 ---|
   |              |
   每次 ~1-5ms（取决于网络和索引）
```

40 次查询 = 40 次网络往返 = 40~200ms
而 1 次批量查询 = 1 次网络往返 = 1~5ms

**这就是 N+1 问题的本质：用 N 次网络往返做了 1 次就能搞定的事。**

### 修复

```java
/** 批量确保股票记录存在（1 次批量查询 + 按需 insert/update） */
private void batchEnsureStockRecords(List<MarketQuoteVO> quotes) {
    List<String> symbols = quotes.stream().map(MarketQuoteVO::getSymbol).toList();

    // 一次性查出所有已存在的股票（1 次查询，不是 40 次）
    List<StockDO> existingStocks = stockMapper.selectList(
        new LambdaQueryWrapper<StockDO>().in(StockDO::getSymbol, symbols)
    );
    Map<String, StockDO> existingMap = existingStocks.stream()
        .collect(Collectors.toMap(StockDO::getSymbol, Function.identity()));

    List<StockDO> toInsert = new ArrayList<>();
    List<StockDO> toUpdate = new ArrayList<>();

    // 内存中分类（0 次 IO）
    for (MarketQuoteVO quote : quotes) {
        StockDO existing = existingMap.get(quote.getSymbol());
        if (existing != null) {
            if (nameChanged(quote, existing)) {
                toUpdate.add(existing);
            }
        } else {
            toInsert.add(createNewStock(quote));
        }
    }

    // 批量写入（2 次查询，不是 40 次）
    for (StockDO s : toInsert) stockMapper.insert(s);
    for (StockDO s : toUpdate) stockMapper.updateById(s);
}
```

### 核心原理

> **循环里不查库。先批量查，再在内存中处理，最后批量写。**

识别 N+1 问题的口诀：
- 看到 `for` 循环里面有数据库调用 → 大概率是 N+1
- 看到 `selectOne` / `selectById` 在循环里 → 100% 是 N+1

---

## 三、冗余数据库查询

### 现象

`PaperTradingService.createCashTransfer` 中：

```java
paperAccountMapper.updateById(account);      // 第 1 次：更新余额
refreshAccountSnapshot(account.getId());      // 第 2 次：重新查 account + 所有持仓
PaperAccountDO updatedAccount = paperAccountMapper.selectById(account.getId()); // 第 3 次：又查一次 account
transactionEventProducer.send(new TransactionEvent(
    ..., updatedAccount.getCashBalance(), ...  // 用第 3 次查出来的值
));
```

同一个事务里，`account` 被查了 **3 次**。

### 为什么这是问题

每次数据库查询都有成本：
1. 网络往返（1-5ms）
2. 数据库解析 SQL、查索引、读数据页
3. 序列化/反序列化结果集

3 次查询 ≈ 3-15ms 的纯浪费。更重要的是，这暴露了代码逻辑混乱——开发者不清楚"对象更新后状态是否同步"。

### 修复

```java
paperAccountMapper.updateById(account);
refreshAccountSnapshot(account.getId());

// 直接用内存中的 account，它已经在 updateById 后是最新的
transactionEventProducer.send(new TransactionEvent(
    ..., account.getCashBalance(), ...  // ← 不再重复查询
));
```

### 核心原理

> **ORM 的 `updateById` 不会改变 Java 对象的状态，但对象本身的字段已经在 `setXxx()` 时更新了。所以更新后直接用原对象即可。**

什么时候需要重新查库？
- 需要数据库默认值（如 `DEFAULT CURRENT_TIMESTAMP`）
- 有触发器修改了数据
- 有其他进程/线程可能并发修改

其他情况，直接用内存对象。

---

## 四、串行统计查询改并行

### 现象

`AdminService.getDashboard` 执行 9 次 `selectCount`：

```java
long totalUsers = userMapper.selectCount(null);
long totalVipUsers = userMembershipMapper.selectCount(...);
long totalAdminUsers = userMapper.selectCount(...);
long totalAiSessions = aiSessionMapper.selectCount(...);
long totalHandoffTickets = aiHandoffTicketMapper.selectCount(...);
long openHandoffTickets = aiHandoffTicketMapper.selectCount(...);
long totalWatchlists = watchlistMapper.selectCount(...);
long totalPaperAccounts = paperAccountMapper.selectCount(...);
long totalTransactionLogs = transactionLogMapper.selectCount(...);
```

9 次串行执行，总耗时 = sum(9次)。

### 为什么这是问题

```
时间线（串行）：
|--查询1--|--查询2--|--查询3--|...|--查询9--|
总耗时 = 9 × 平均查询时间 ≈ 9 × 3ms = 27ms
```

虽然每次查询很快，但 9 次累加起来就是 27ms，而且这 9 个查询之间**没有任何数据依赖**。

### 修复

```java
// 9 个独立查询并行执行
CompletableFuture<Long> fTotalUsers = CompletableFuture.supplyAsync(
    () -> userMapper.selectCount(null));
CompletableFuture<Long> fTotalVip = CompletableFuture.supplyAsync(
    () -> userMembershipMapper.selectCount(...));
// ... 其他 7 个

// 等所有查询完成
CompletableFuture.allOf(fTotalUsers, fTotalVip, ...).join();

// 取结果
return new AdminDashboardVO(
    fTotalUsers.join(), fTotalVip.join(), ...
);
```

```
时间线（并行）：
|--查询1--|
|--查询2--|
|--查询3--|  ...  （同时执行）
|--查询9--|
总耗时 = max(9次) ≈ 3ms
```

### 核心原理

> **没有数据依赖的查询，用 `CompletableFuture`（Java）或 `Promise.all`（JavaScript）并行执行。总耗时从"求和"变成"取最大值"。**

---

## 五、前端无差别全量刷新

### 现象

`refreshAll` 在每次聊天结束后被调用，一次性发 18 个 HTTP 请求：

```typescript
export const refreshAll = async () => {
  await Promise.all([
    optionalTask('profile', fetchProfile),
    optionalTask('membership', ...),
    optionalTask('quotas', ...),
    optionalTask('quotes', fetchQuotes),       // 行情数据 — 聊天不需要
    optionalTask('market stocks', ...),         // 股票列表 — 聊天不需要
    optionalTask('hot news', ...),              // 新闻 — 聊天不需要
    optionalTask('sectors', ...),               // 板块 — 聊天不需要
    optionalTask('watchlists', ...),            // 自选 — 聊天不需要
    optionalTask('paper account', ...),         // 模拟盘 — 聊天不需要
    optionalTask('chat sessions', ...),         // 会话列表 — ← 只需要这个
    optionalTask('handoff tickets', ...),       // 工单 — 聊天不需要
    optionalTask('paper transactions', ...),    // 交易记录 — 聊天不需要
    optionalTask('notifications', ...),         // 通知 — 可能需要
    optionalTask('announcements', ...),         // 公告 — 聊天不需要
    optionalTask('admin workspace', ...),       // 管理后台 — 聊天不需要
  ])
}
```

聊天结束后，用户只是想知道"新会话创建了吗"和"配额还剩多少"，不需要刷新模拟盘、行情、管理后台等数据。

### 为什么这是问题

1. **带宽浪费**：18 个请求的响应数据大部分用不到
2. **后端压力**：每个请求都要鉴权、查库、序列化
3. **用户感知变慢**：虽然用了 `Promise.all` 并行，但 18 个请求同时到达，服务器处理时间变长
4. **移动端不友好**：手机网络下，18 个并发请求可能导致连接数超限

### 修复

```typescript
/** 聊天结束后只刷新必要的上下文 */
export const refreshChatContext = async () => {
  if (!store.user) return
  await safe(async () => {
    await Promise.all([
      optionalTask('chat sessions', fetchSessions),     // 新会话标题
      optionalTask('quotas', () => get(`${API}/quotas/me`, 'quotas')),  // 剩余额度
      optionalTask('notifications', () => get(`${API}/notifications`, 'notifications')),  // 新通知
    ])
  })
}
```

从 18 个请求降到 3 个，减少 83% 的网络开销。

### 核心原理

> **按需加载：只请求当前场景需要的数据。不同场景（聊天、浏览行情、管理后台）有不同的数据需求，不要用一个"万能刷新"函数覆盖所有场景。**

---

## 总结：五个优化原则

| # | 原则 | 一句话 |
|---|------|--------|
| 1 | 复用重量级资源 | 线程池、连接池提为模块级常量 |
| 2 | 消除 N+1 | 循环里不查库，先批量查再内存处理 |
| 3 | 消除冗余查询 | 更新后直接用对象，不重复 select |
| 4 | 独立查询并行化 | 无依赖的查询用 CompletableFuture / Promise.all |
| 5 | 按需加载 | 不同场景只请求需要的数据 |

记住这五条，面试被问"你做过什么性能优化"，就能拿出真实的项目案例来讲。

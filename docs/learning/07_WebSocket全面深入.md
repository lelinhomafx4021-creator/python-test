# 07 - WebSocket 全面深入（结合项目实战）

## 一、WebSocket 是什么

HTTP 是"你问我答"：客户端发请求，服务器回响应，连接断开。

WebSocket 是"打电话"：拨通一次，双方随时都能说话，直到挂断。

```
HTTP:    客户端 --问--> 服务器 --答--> 断开
         客户端 --问--> 服务器 --答--> 断开
         （每次都要重新拨号）

WebSocket: 客户端 --拨通--> 服务器
           客户端 --说话--> 服务器
           服务器 --说话--> 客户端
           服务器 --说话--> 客户端
           （一直通着，谁都能先开口）
```

## 二、WebSocket 连接生命周期

```
┌──────────┐                              ┌──────────┐
│  客户端   │                              │  服务器   │
└────┬─────┘                              └────┬─────┘
     │                                         │
     │  1. HTTP 请求（带 Upgrade: websocket）    │  ← 握手阶段
     │  ───────────────────────────────────────>│
     │                                         │
     │  2. HTTP 101 Switching Protocols        │
     │  <───────────────────────────────────────│
     │                                         │
     │  ═══════ 连接建立，切换到 WS 协议 ═══════  │
     │                                         │
     │  3. 客户端发消息                          │  ← 通信阶段
     │  ───────────────────────────────────────>│
     │                                         │
     │  4. 服务器推消息                          │
     │  <───────────────────────────────────────│
     │                                         │
     │  5. 服务器推消息                          │
     │  <───────────────────────────────────────│
     │                                         │
     │  ═══════ 任意一方关闭连接 ═══════════════  │
     │                                         │
     │  6. 关闭帧                              │
     │  ───────────────────────────────────────>│
     │                                         │
```

**四个阶段：握手 → 通信 → 心跳（可选）→ 关闭**

## 三、服务端实现（Spring Boot）

### 3.1 项目结构

```
market/websocket/
├── MarketWebSocketHandler.java    ← 处理器（核心逻辑）
└── MarketWebSocketConfig.java     ← 配置（注册端点）
```

### 3.2 配置类 — 注册 WebSocket 端点

```java
@Configuration
@EnableWebSocket  // 启用 WebSocket 支持
public class MarketWebSocketConfig implements WebSocketConfigurer {

    private final MarketWebSocketHandler marketWebSocketHandler;

    public MarketWebSocketConfig(MarketWebSocketHandler marketWebSocketHandler) {
        this.marketWebSocketHandler = marketWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketWebSocketHandler, "/ws/market")  // 注册端点路径
                .setAllowedOrigins("*");  // 允许所有来源连接（生产环境要限制）
    }
}
```

做了两件事：
- `@EnableWebSocket` — 告诉 Spring "我要用 WebSocket"
- `registry.addHandler(...)` — 把处理器绑定到 `/ws/market` 路径

### 3.3 处理器 — 核心逻辑

继承 `TextWebSocketHandler`，重写四个生命周期方法：

```java
@Component
@EnableScheduling
public class MarketWebSocketHandler extends TextWebSocketHandler {

    // 每个连接订阅了哪些股票
    private final Map<WebSocketSession, Set<String>> subscriptions = new ConcurrentHashMap<>();

    // ① 连接建立
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        subscriptions.put(session, ConcurrentHashMap.newKeySet());
        // 给这个 session 初始化一个空的订阅列表
    }

    // ② 收到客户端消息
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 解析 JSON，处理 subscribe / unsubscribe
    }

    // ③ 连接关闭
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptions.remove(session);
        // 清理这个 session 的订阅数据
    }

    // ④ 连接异常
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        subscriptions.remove(session);
        session.close();
        // 出错了就清理并关闭
    }
}
```

**四个方法对应连接的四个时刻：**

| 方法 | 什么时候触发 | 做什么 |
|------|------------|--------|
| `afterConnectionEstablished` | 客户端连上时 | 初始化 |
| `handleTextMessage` | 收到消息时 | 处理业务 |
| `afterConnectionClosed` | 连接断开时 | 清理资源 |
| `handleTransportError` | 网络异常时 | 报错 + 关闭 |

### 3.4 消息处理 — 收到客户端消息

客户端发 JSON，服务端解析并处理：

```java
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    JsonNode root = objectMapper.readTree(message.getPayload());
    String action = root.path("action").asText("");
    JsonNode symbolsNode = root.path("symbols");

    if ("subscribe".equalsIgnoreCase(action)) {
        // 加入订阅列表
        Set<String> subs = subscriptions.get(session);
        symbolsNode.forEach(node -> subs.add(node.asText()));

    } else if ("unsubscribe".equalsIgnoreCase(action)) {
        // 从订阅列表移除
        Set<String> subs = subscriptions.get(session);
        symbolsNode.forEach(node -> subs.remove(node.asText()));
    }
}
```

**协议约定：**
```
客户端 → 服务端：{"action": "subscribe",   "symbols": ["600519", "601179"]}
客户端 → 服务端：{"action": "unsubscribe", "symbols": ["600519"]}
```

### 3.5 主动推送 — 定时推行情

```java
@Scheduled(fixedDelay = 3000)  // 每 3 秒执行一次
public void pushQuotes() {
    // 1. 收集所有客户端订阅的股票代码
    Set<String> allSymbols = new LinkedHashSet<>();
    for (Set<String> subs : subscriptions.values()) {
        allSymbols.addAll(subs);
    }

    // 2. 批量拉取行情
    List<MarketQuoteVO> quotes = marketService.refreshQuotes(new ArrayList<>(allSymbols));

    // 3. 遍历每个客户端，推送它订阅的行情
    for (Map.Entry<WebSocketSession, Set<String>> entry : subscriptions.entrySet()) {
        WebSocketSession session = entry.getKey();
        Set<String> subs = entry.getValue();

        if (!session.isOpen()) continue;  // 连接已断开就跳过

        for (String symbol : subs) {
            MarketQuoteVO q = quoteMap.get(symbol);
            // 构建 JSON 并发送
            session.sendMessage(new TextMessage(json));
        }
    }
}
```

**关键点：** WebSocket 服务器可以**主动**推消息，不需要客户端先请求。这就是和 HTTP 最大的区别。

### 3.6 数据存储 — ConcurrentHashMap

```java
// 结构：每个连接 → 它订阅的股票集合
Map<WebSocketSession, Set<String>> subscriptions = new ConcurrentHashMap<>();
```

```
Session A  →  {"600519", "601179"}
Session B  →  {"601179", "000001"}
Session C  →  {"600519"}
```

为什么用 `ConcurrentHashMap`？因为多个线程同时读写（定时任务在推、客户端在订阅），需要线程安全。

## 四、客户端实现（Vue 3 + TypeScript）

### 4.1 连接

```typescript
function connect() {
  const url = 'ws://localhost:8080/ws/market'
  ws = new WebSocket(url)

  ws.onopen = () => {
    console.log('[WS] 连接成功')
    connected.value = true
    // 重连后自动重新订阅
    if (subscribedSymbols.size > 0) {
      subscribe(Array.from(subscribedSymbols))
    }
  }

  ws.onmessage = handleMessage  // 收到消息

  ws.onclose = () => {
    connected.value = false
    scheduleReconnect()  // 断了就重连
  }

  ws.onerror = (err) => {
    console.error('[WS] 连接错误', err)
  }
}
```

**四个事件对应服务端的四个方法：**

| 客户端事件 | 服务端方法 | 方向 |
|-----------|-----------|------|
| `ws.onopen` | `afterConnectionEstablished` | 客户端知道连上了 |
| `ws.onmessage` | `handleTextMessage`（反向） | 客户端收到消息 |
| `ws.onclose` | `afterConnectionClosed` | 客户端知道断了 |
| `ws.onerror` | `handleTransportError` | 客户端知道出错了 |

### 4.2 发送消息（订阅/取消订阅）

```typescript
function subscribe(symbols: string[]) {
  // 记录到本地
  symbols.forEach(s => subscribedSymbols.add(s))
  // 发给服务器
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ action: 'subscribe', symbols }))
  }
}

function unsubscribe(symbols: string[]) {
  symbols.forEach(s => subscribedSymbols.delete(s))
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ action: 'unsubscribe', symbols }))
  }
}
```

### 4.3 收到消息 → 更新页面

```typescript
function handleMessage(msg: MessageEvent) {
  const data = JSON.parse(msg.data)
  // { symbol: "600519", price: 1800.00, change: 15.5, ... }

  // 更新 store 里的行情数据 → 页面自动刷新
  const idx = store.quotes.findIndex(q => q.symbol === data.symbol)
  if (idx >= 0) {
    store.quotes[idx].lastPrice = data.price
    store.quotes[idx].changePercent = data.changePct
  }
}
```

### 4.4 自动重连（指数退避）

```typescript
let reconnectDelay = 1000  // 初始 1 秒

function scheduleReconnect() {
  if (manualClose) return  // 主动断开不重连

  setTimeout(() => {
    connect()
  }, reconnectDelay)

  // 指数退避：1s → 2s → 4s → 8s → 16s → 30s（封顶）
  reconnectDelay = Math.min(reconnectDelay * 2, 30000)
}
```

**为什么指数退避？** 如果服务器挂了，所有客户端同时疯狂重连会把服务器压垮。逐渐拉长间隔，减轻压力。

```
第1次重连：等 1 秒
第2次重连：等 2 秒
第3次重连：等 4 秒
第4次重连：等 8 秒
...
封顶 30 秒
```

连接成功后重置为 1 秒。

## 五、完整数据流

```
用户打开行情页面
       │
       ▼
前端调用 connect()  ────→  服务端 afterConnectionEstablished()
       │
       ▼
前端调用 subscribe(["600519"])
       │
       │  ws.send({"action":"subscribe","symbols":["600519"]})
       ▼
服务端 handleTextMessage()  →  记录到 subscriptions Map
       │
       ▼
服务端 @Scheduled 每3秒 pushQuotes()
       │
       │  拉取行情 → 遍历订阅者 → session.sendMessage()
       ▼
前端 ws.onmessage  →  handleMessage()  →  更新 store  →  页面刷新
       │
       ▼
用户关闭页面
       │
       ▼
前端 ws.close()  ────→  服务端 afterConnectionClosed()  →  清理 subscriptions
```

## 六、WebSocket vs HTTP vs SSE 对比

| | HTTP | SSE | WebSocket |
|---|------|-----|-----------|
| 连接 | 短连接 | 长连接 | 长连接 |
| 方向 | 客户端→服务器 | 服务器→客户端 | 双向 |
| 协议 | HTTP | HTTP | 独立（ws://） |
| 握手 | 每次请求 | 一次 | 一次 |
| 服务器主动推 | 不支持 | 支持 | 支持 |
| 客户端发消息 | 支持 | 不支持 | 支持 |
| 自动重连 | 不需要 | 浏览器内置 | 需手动实现 |
| 适用场景 | REST API | AI 流式输出 | 实时行情、聊天 |

## 七、常见问题

### 7.1 连接断了怎么办？

客户端实现自动重连 + 指数退避（项目已实现）。

### 7.2 怎么知道连接还活着？

**心跳机制：** 定期发 ping/pong 帧。

```java
// 服务端配置心跳
@Override
public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/market")
            .setAllowedOrigins("*")
            .withSockJS();  // SockJS 内置心跳
}
```

```typescript
// 或者客户端自己发心跳
setInterval(() => {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ action: 'ping' }))
  }
}, 30000)  // 每 30 秒发一次
```

### 7.3 多人同时订阅怎么存？

用 `ConcurrentHashMap<WebSocketSession, Set<String>>`：
- key = 每个客户端连接
- value = 这个连接订阅的股票
- 遍历所有连接，推各自订阅的数据

### 7.4 生产环境要注意什么？

| 问题 | 方案 |
|------|------|
| 跨域 | `setAllowedOrigins()` 限制具体域名，别用 `*` |
| 鉴权 | 握手时检查 Token（通过 `HandshakeInterceptor`） |
| 集群多实例 | 用 Redis 发布订阅同步消息（不能只存在内存里） |
| 连接数上限 | Nginx 配置 `proxy_read_timeout` 和最大连接数 |

## 八、一句话总结

- **服务端：** 继承 `TextWebSocketHandler`，重写四个生命周期方法，用 `session.sendMessage()` 推消息
- **客户端：** `new WebSocket(url)`，监听四个事件，用 `ws.send()` 发消息
- **核心：** 连接建立后双方随时互发，不需要客户端轮询

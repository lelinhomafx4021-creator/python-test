# 06 - WebSocket 与 HTTP 全面对比

## 一、本质区别

| | HTTP | WebSocket |
|---|------|-----------|
| 连接方式 | 短连接（请求→响应→断开） | 长连接（一次握手，持续通信） |
| 通信方向 | 单向（客户端先发） | 双向（任何一方都能先发） |
| 协议标识 | `http://` / `https://` | `ws://` / `wss://` |
| 数据格式 | 文本（HTML/JSON/...） | 文本或二进制帧 |
| 状态 | 无状态（每次请求独立） | 有状态（连接期间保持上下文） |

## 二、HTTP 工作模型

```
客户端                      服务器
  |  --- GET /data -------->  |
  |  <--- 200 OK + 数据 ----  |
  |      （连接断开）          |
  |                           |
  |  --- GET /data -------->  |  （重新建立连接）
  |  <--- 200 OK + 数据 ----  |
  |      （连接断开）          |
```

**特点：**
- 每次请求都要建立 TCP 连接（HTTP/1.1 有 keep-alive 复用，但本质还是请求-响应）
- 客户端问一次，服务器答一次
- 服务器不能主动给客户端推消息

**缺点：**
- 实时性差：想拿新数据只能轮询（每隔 N 秒请求一次）
- 轮询浪费：大部分请求返回"没有新数据"
- Header 开销大：每次请求都带完整的 Header（Cookie、User-Agent 等）

## 三、WebSocket 工作模型

```
客户端                      服务器
  |  --- HTTP 升级请求 ----->  |  （握手阶段，借用 HTTP）
  |  <-- 101 Switching -----  |
  |      （连接建立，持久保持）  |
  |                           |
  |  --- 消息1 ------------->  |  （客户端发）
  |  <--- 消息2 ------------  |  （服务器主动推）
  |  <--- 消息3 ------------  |  （服务器连推两条）
  |  --- 消息4 ------------->  |
  |      （任意一方可关闭）      |
```

**特点：**
- 一次握手后，TCP 连接一直保持
- 双向通信，服务器可以主动推消息
- Header 只在握手时发一次，后续数据帧开销极小（2~14 字节）

## 四、握手过程（WebSocket 如何建立连接）

WebSocket 借用 HTTP 完成握手，然后协议升级：

```
GET /chat HTTP/1.1
Host: example.com
Upgrade: websocket          ← 告诉服务器：我要升级成 WebSocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13

---

HTTP/1.1 101 Switching Protocols   ← 服务器同意升级
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

握手完成后，HTTP 协议退场，后续全部走 WebSocket 帧协议。

## 五、使用场景对比

| 场景 | 用什么 | 原因 |
|------|-------|------|
| 普通网页加载 | HTTP | 请求一次就够了 |
| 表单提交 | HTTP | 一次性操作 |
| REST API | HTTP | 标准增删改查 |
| AI 流式输出（SSE） | HTTP SSE | 服务器单向推，不需要客户端频繁发 |
| 聊天室 | WebSocket | 双向实时通信 |
| 在线游戏 | WebSocket | 低延迟、高频双向数据 |
| 股票实时行情 | WebSocket | 服务器持续推价格变动 |
| 协同编辑（多人文档） | WebSocket | 多人同时操作，实时同步 |
| 通知推送 | WebSocket | 服务器主动告知客户端 |

## 六、SSE vs WebSocket

SSE（Server-Sent Events）是基于 HTTP 的单向流式推送，和 WebSocket 容易混淆：

| | SSE | WebSocket |
|---|-----|-----------|
| 方向 | 服务器→客户端（单向） | 双向 |
| 协议 | HTTP | 独立协议（ws://） |
| 重连 | 浏览器自动重连 | 需手动实现 |
| 数据格式 | 纯文本 | 文本 + 二进制 |
| 适用 | 流式推送（AI输出、通知） | 双向交互（聊天、游戏） |

## 七、代码对比

### HTTP 请求（Spring Boot）

```java
@GetMapping("/quotes")
public Result quotes(@RequestParam String symbol) {
    return Result.ok(service.getQuote(symbol));
}
```

### SSE 流式推送（Spring Boot）

```java
@GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestParam String question) {
    return aiService.streamAnswer(question);  // 持续推送文本片段
}
```

### WebSocket（Spring Boot）

```java
// 服务端
@ServerEndpoint("/ws/chat")
public class ChatSocket {
    @OnMessage
    public void onMessage(String msg, Session session) {
        session.getBasicRemote().sendText("收到: " + msg);
    }
}

// 客户端（JavaScript）
const ws = new WebSocket("ws://localhost:8080/ws/chat");
ws.onmessage = (e) => console.log(e.data);  // 服务器推来的消息
ws.send("你好");                              // 发消息给服务器
```

## 八、性能对比

| | HTTP | WebSocket |
|---|------|-----------|
| 连接开销 | 每次请求都要握手（或复用连接） | 一次握手，长期复用 |
| 头部开销 | 每次几百字节 Header | 握手后 2~14 字节帧头 |
| 延迟 | 高（每次建立连接） | 低（连接已存在） |
| 服务器资源 | 请求结束即释放 | 连接期间持续占用 |
| 并发限制 | 受限于连接数 | 每个连接长期占用，压力更大 |

## 九、一句话总结

- **HTTP：** 你问我答，问完就走
- **SSE：** 你问一次，我一直说（单向流）
- **WebSocket：** 建立连接后，谁都能说话（双向实时）

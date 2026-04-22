# 教程 05：Java 安全架构 —— 注解 + 拦截器 + 上下文

## 一句话概念
这套安全体系模拟了企业中的"门禁系统"：
- **@LoginRequired** = 门上的标牌（"此门需要刷卡"）
- **LoginInterceptor** = 保安大哥（检查你的工牌）
- **UserContext** = 访客胸牌（进门后所有人都能看到你是谁）

---

## 1. 为什么不直接在 Controller 里写校验？

```java
// ❌ 错误做法：每个方法都手动校验
@PostMapping("/chat")
public ApiResult chat(@RequestHeader("X-User-Id") String userId) {
    if (!userService.isValidUser(userId)) {  // 重复代码！
        return ApiResult.fail(401, "非法请求");
    }
    // ... 业务逻辑
}

@GetMapping("/sessions")
public ApiResult sessions(@RequestHeader("X-User-Id") String userId) {
    if (!userService.isValidUser(userId)) {  // 又重复了！
        return ApiResult.fail(401, "非法请求");
    }
    // ... 业务逻辑
}
```

问题：**校验逻辑散落在每个方法里，违反 DRY 原则**。

---

## 2. 正确做法：三件套

### 2.1 自定义注解 `@LoginRequired`

```java
@Target({ElementType.METHOD, ElementType.TYPE})  // 可以打在方法或类上
@Retention(RetentionPolicy.RUNTIME)               // 运行时可读取
public @interface LoginRequired { }
```

| 属性 | 含义 |
|------|------|
| `@Target(METHOD)` | 可以标注在方法上 |
| `@Target(TYPE)` | 可以标注在类上（整个 Controller 的所有方法都生效） |
| `@Retention(RUNTIME)` | 注解信息保留到运行时（拦截器才能通过反射读到） |

### 2.2 拦截器 [LoginInterceptor](file:///d:/ai-investor/java-ai-gateway/src/main/java/com/aiinvestor/gateway/interceptor/LoginInterceptor.java#24-83)

```java
public boolean preHandle(HttpServletRequest request, 
                         HttpServletResponse response, 
                         Object handler) {
    // 1. 检查是否是 Controller 方法
    if (!(handler instanceof HandlerMethod)) return true;
    
    // 2. 检查注解
    HandlerMethod hm = (HandlerMethod) handler;
    boolean isRequired = hm.hasMethodAnnotation(LoginRequired.class)
                      || hm.getBeanType().isAnnotationPresent(LoginRequired.class);
    if (!isRequired) return true;  // 没有注解，直接放行
    
    // 3. 执行校验
    String userIdStr = request.getHeader("X-User-Id");
    // ... 查库验证 ...
    
    // 4. 存入上下文
    UserContext.set(user);
    return true;
}
```

### 2.3 全局上下文 [UserContext](file:///d:/ai-investor/java-ai-gateway/src/main/java/com/aiinvestor/gateway/context/UserContext.java#14-35)

```java
public class UserContext {
    private static final ThreadLocal<UserDO> USER_HOLDER = new ThreadLocal<>();
    
    public static void set(UserDO user)  { USER_HOLDER.set(user); }
    public static UserDO get()           { return USER_HOLDER.get(); }
    public static void remove()          { USER_HOLDER.remove(); }
}
```

---

## 3. ThreadLocal 原理（面试高频题）

```
线程 A（请求1）: ThreadLocal → {id: 1, name: "张三"}
线程 B（请求2）: ThreadLocal → {id: 2, name: "李四"}
线程 C（请求3）: ThreadLocal → {id: 3, name: "王五"}
```

- 每个线程有自己**独立的变量副本**
- 线程 A 读 `UserContext.get()` → 张三
- 线程 B 读 `UserContext.get()` → 李四
- **互不干扰！**

### 为什么必须 [remove()](file:///d:/ai-investor/java-ai-gateway/src/main/java/com/aiinvestor/gateway/context/UserContext.java#31-34)？

```java
@Override
public void afterCompletion(...) {
    UserContext.remove();  // 请求结束，必须清理！
}
```

因为 Tomcat 使用**线程池**，线程会被复用。如果不清理，下一个请求可能"继承"上一个请求的用户信息 → **越权漏洞**！

> **面试点**：ThreadLocal 内存泄漏是经典考题。答案是"线程池 + 没有 remove = 泄漏"。

---

## 4. 使用效果对比

```java
// 🔴 重构前：参数传递，手动校验
@PostMapping("/chat")
public ApiResult chat(@RequestHeader("X-User-Id") String userId) {
    if (!userService.isValidUser(userId)) { ... }
    pythonAiClientService.callChat(message, userId, sessionId);
}

// 🟢 重构后：注解声明，全局上下文
@LoginRequired  // 一个注解搞定校验
@PostMapping("/chat")
public ApiResult chat(@RequestBody AiChatRequest request) {
    Long userId = UserContext.getUserId();  // 随时随地取用户
    pythonAiClientService.callChat(message, String.valueOf(userId), sessionId);
}
```

---

## 5. Interceptor vs Filter vs AOP

| 特性 | Filter | Interceptor | AOP |
|------|--------|-------------|-----|
| 规范 | Servlet | Spring MVC | Spring |
| 能读注解？ | ❌ | ✅ | ✅ |
| 能拿到 Controller？ | ❌ | ✅ | ✅ |
| 执行时机 | 最早 | 在 Controller 前后 | 方法级别 |
| 适合场景 | 编码/CORS | **登录校验** | 日志/事务 |

> **面试点**：我们从 Filter 迁到 Interceptor，就是为了能读到 `@LoginRequired` 注解，实现"声明式鉴权"。


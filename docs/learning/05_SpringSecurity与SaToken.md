# Spring Security + Sa-Token 认证架构笔记

## 一、为什么要两套框架一起用？

很多项目要么用 Spring Security，要么用 Sa-Token，我们这个项目**两个都用**，但各有分工：

```
┌──────────────────────────────────────────┐
│           Spring Security               │
│  只做一件事：CORS 跨域处理                │
│  CSRF / 登录 / 鉴权 → 全部关掉了          │
└──────────────┬───────────────────────────┘
               │ 放行后
               ▼
┌──────────────────────────────────────────┐
│             Sa-Token                     │
│  真正的认证框架：                        │
│  - 登录 StpUtil.login()                  │
│  - 签发 Token                            │
│  - 会话管理                              │
│  - 踢人下线                              │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│  自定义 @LoginRequired + LoginInterceptor │
│  注解驱动的鉴权拦截器                     │
└──────────────────────────────────────────┘
```

---

## 二、Spring Security 基础概念

### 2.1 过滤器链（Filter Chain）

Spring Security 的核心是一串过滤器，像安检流水线：

```
请求 → Filter1 → Filter2 → ... → FilterN → Controller
```

每个 Filter 可以做不同的事：验证身份、检查权限、处理 CORS 等。

### 2.2 核心术语

| 术语 | 含义 | 本项目怎么处理 |
|------|------|---------------|
| Authentication | 你是谁（身份认证） | 交给 Sa-Token |
| Authorization | 你能干什么（权限校验） | 交给 Sa-Token |
| CSRF | 跨站请求伪造攻击 | 关闭（前后端分离+Token鉴权，CSRF攻击路径不存在） |
| CORS | 跨域资源共享 | Spring Security 处理 |
| Principal | 当前登录用户 | 存在 UserContext（ThreadLocal） |

### 2.3 SecurityFilterChain 配置详解

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1. 关闭 CSRF
        //    CSRF 只在 Cookie+Session 模式下有攻击面
        //    前后端分离 + Token 鉴权不存在 CSRF 问题
        .csrf(AbstractHttpConfigurer::disable)

        // 2. 启用 CORS，使用自定义的跨域配置
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // 3. 所有请求放行（鉴权交给 Sa-Token）
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

        // 4. 关闭 Spring Security 自带的登录页
        //    我们用自己写的 /gateway/auth/login
        .formLogin(AbstractHttpConfigurer::disable)

        // 5. 关闭 HTTP Basic 认证
        .httpBasic(AbstractHttpConfigurer::disable);

    return http.build();
}
```

### 2.4 为什么不能完全排除 Spring Security？

关键代码在 SecurityConfig：

```java
// CorsConfigurationSource 需要 SecurityFilterChain 来生效
// 完全排除 Spring Security 后需要自己写 CORS Filter，没必要
```

解释：
- CORS 的 `OPTIONS` 预检请求**必须在所有鉴权之前处理**
- Spring Security 的过滤器链天然排在 DispatcherServlet 前面
- 如果自己写 Filter，需要处理优先级、注册顺序等一堆问题
- 借用 Spring Security 的架子只需要写一个 Bean，省事且可靠

---

## 三、CORS 跨域深入理解

### 3.1 什么是同源策略？

浏览器安全机制：`http://localhost:5173`（前端）不能随便请求 `http://localhost:8080`（后端）。

**同源 = 协议 + 域名 + 端口全相同**

| 前端 | 后端 | 同源？ |
|------|------|--------|
| localhost:5173 | localhost:8080 | ❌ 端口不同 |
| localhost:5173 | api.example.com | ❌ 域名不同 |
| https://a.com | http://a.com | ❌ 协议不同 |

### 3.2 CORS 的工作流程

```
前端发起跨域请求（如 POST /api/xxx）
    ↓
浏览器自动先发一个 OPTIONS 请求（预检 Preflight）
    ↓
后端返回 CORS 头：
  Access-Control-Allow-Origin: http://localhost:5173
  Access-Control-Allow-Methods: GET,POST,PUT,DELETE
  Access-Control-Allow-Headers: Content-Type,satoken
    ↓
浏览器检查响应头，符合规则才发真正的请求
    ↓
真正的 POST 请求发出
```

### 3.3 项目 CORS 配置解读

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    // 允许的来源：从配置文件读取，支持通配符
    // 如 http://localhost:* 或具体域名
    config.setAllowedOriginPatterns(corsProperties.getAllowedOriginList());

    // 允许的 HTTP 方法
    // OPTIONS 是预检请求必须的
    config.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","OPTIONS"));

    // 允许的请求头
    // satoken 头：Sa-Token 通过 Header 传递 token
    // X-Trace-Id：分布式链路追踪
    config.setAllowedHeaders(Arrays.asList(
        "Authorization","Content-Type","X-User-Id","X-Trace-Id","satoken"
    ));

    // 暴露的响应头：前端 JS 能读取 satoken 头
    config.setExposedHeaders(Arrays.asList("satoken"));

    // 允许携带 Cookie（true 时 origin 不能用 * 必须指定具体值）
    config.setAllowCredentials(true);

    // 预检请求缓存 1 小时，减少 OPTIONS 请求
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

**面试常问：为什么 allowCredentials=true 时 origin 不能用 `*`？**

因为浏览器规范规定：携带凭证（Cookie）时，必须明确允许的来源，`*` 太危险——相当于任何网站都能带着你的 Cookie 发请求。

---

## 四、Sa-Token 核心用法

### 4.1 为什么选 Sa-Token 而不是 Spring Security 做认证？

| 对比维度 | Spring Security | Sa-Token |
|---------|----------------|----------|
| 学习成本 | 极高（AuthenticationManager、Provider、Filter 链...） | 极低（几个静态方法） |
| 登录代码 | 至少 5-6 个类 | `StpUtil.login(id)` 一行 |
| Token 模式 | 需额外集成 JWT 库 | 内置多种 Token 风格 |
| Session 存储 | 内存 / Spring Session | 10+ 种（Redis、MongoDB 等） |
| 权限注解 | `@PreAuthorize` | `@SaCheckPermission` |
| 踢人下线 | 需要自己实现 | `StpUtil.kickout(id)` |
| 文档 | 英文，分散 | 中文，集中 |

### 4.2 项目中 Sa-Token 的使用

**登录（AuthController）：**

```java
// 验证用户名密码后
StpUtil.login(user.getId());
// Sa-Token 自动生成 Token，默认存到 Cookie 里
// 也可以配置为 Header 模式

// 获取当前 Token 返回给前端
String token = StpUtil.getTokenValue();
```

**鉴权（LoginInterceptor）：**

```java
// 检查当前请求是否已登录
// 未登录会抛 NotLoginException
StpUtil.checkLogin();

// 获取登录时存入的 ID（即 userId）
Object loginId = StpUtil.getLoginIdDefaultNull();
```

**登出（AuthController）：**

```java
// 清除当前会话
StpUtil.logout();

// 或者通过 ID 踢人下线
StpUtil.logout(userId);  // 指定 userId
StpUtil.kickout(userId); // 强制踢下线（标记+清除）
```

### 4.3 Sa-Token 的 Token 传递方式

Sa-Token 默认从三个地方找 Token：

```
优先级从高到低：
1. Cookie → "satoken" 这个 Cookie
2. Header → "satoken" 请求头
3. 请求参数 → ?satoken=xxx
```

本项目通过 CORS 的 `exposedHeaders` 配合，前端可以先从 Cookie 拿，也可以从 Header 拿。

---

## 五、自定义注解 + 拦截器的鉴权设计

### 5.1 为什么不用 Sa-Token 自带的 @SaCheckLogin？

Sa-Token 也提供了自己的鉴权注解，本项目选择自定义的原因：

1. **更灵活**：可以在拦截器里加载完整用户对象（UserDO），存入 ThreadLocal，后续不用重复查库
2. **更可控**：可以自己加业务逻辑（如用户状态校验、自动清理无效登录态）
3. **注解驱动优于路径匹配**：不用在配置里维护白名单 URL

### 5.2 @LoginRequired 注解

```java
@Target({ElementType.METHOD, ElementType.TYPE})  // 可标在方法或类上
@Retention(RetentionPolicy.RUNTIME)               // 运行时保留，反射可读
@Documented                                        // Javadoc 可见
public @interface LoginRequired {
    // 标记型注解，不需要属性
}
```

使用方式：

```java
// 方式1：标在类上 → 整个 Controller 都需要登录
@LoginRequired
@RestController
public class AiGatewayController { ... }

// 方式2：标在方法上 → 只有这个接口需要登录
@LoginRequired
@GetMapping("/me")
public ApiResult<?> me() { ... }
```

### 5.3 LoginInterceptor 执行流程

```
请求到达
    ↓
是 Controller 方法吗？
    ├─ 不是（静态资源等）→ 直接放行
    └─ 是的
        ↓
    检查方法/类上有 @LoginRequired 吗？
        ├─ 没有 → 放行
        └─ 有的
            ↓
        StpUtil.checkLogin() ← Sa-Token 校验 Token
            ↓
        获取 loginId（userId）
            ↓
        查库加载完整 UserDO
            ↓
        校验用户状态（是否被禁用）
            ↓
        UserContext.set(user) ← 存入 ThreadLocal
            ↓
        Controller 执行...
            ↓
        afterCompletion → UserContext.remove() ← 必须清理！
```

### 5.4 ThreadLocal 与内存泄漏

```java
@Override
public void afterCompletion(...) {
    UserContext.remove(); // 这行绝对不能省！
}
```

**为什么？**

Spring MVC 使用线程池，线程是复用的：

```
线程 T1 处理完 用户A 的请求
    ↓
T1 回到线程池
    ↓
线程 T1 被分配给 用户B 的请求
    ↓
如果没清理 ThreadLocal → 用户B 拿到了用户A 的数据！
```

这就是典型的 **ThreadLocal 数据污染 + 内存泄漏**。

---

## 六、完整请求链路总结

以一个需要登录的请求为例（如 `GET /ai/session/list`）：

```
1. 浏览器发起请求（携带 satoken Cookie/Header）

2. Spring Security 过滤器链
   └─ CORS 过滤器检查：允许这个来源吗？
      └─ 允许 → 继续

3. LoginInterceptor.preHandle()
   ├─ 检查 @LoginRequired 注解
   ├─ StpUtil.checkLogin() 验证 Token
   ├─ 查库加载用户信息
   ├─ 校验用户状态
   └─ UserContext.set(user)

4. Controller 处理业务
   └─ 通过 UserContext.get() 获取当前用户

5. LoginInterceptor.afterCompletion()
   └─ UserContext.remove() 清理

6. 响应返回浏览器
```

---

## 七、常见面试问题

### Q1：项目为什么同时用 Spring Security 和 Sa-Token？

Spring Security 只处理 CORS（因为它的过滤器链来得最早），真正的认证鉴权全交给 Sa-Token。

### Q2：CSRF 为什么可以关？

前后端分离 + Token 鉴权的场景下，Token 在请求头/Cookie 里，不会随 `<img>` 标签等自动携带，CSRF 攻击路径不存在。

### Q3：如何保证 ThreadLocal 不泄漏？

拦截器的 `afterCompletion` 里必须调用 `UserContext.remove()`。这个方法是 final 语义的红线——无论请求成功还是异常都会执行。

### Q4：CORS 预检请求什么时候触发？

浏览器检测到跨域 + 非简单请求时，自动先发 OPTIONS。简单请求的条件很苛刻：只能是 GET/HEAD/POST，且 Content-Type 只能是为 `application/x-www-form-urlencoded`、`multipart/form-data` 或 `text/plain`。现代前端发 JSON（`application/json`）都算非简单请求，所以几乎每次跨域都有预检。

### Q5：`allowCredentials(true)` 为什么 origin 不能是 `*`？

浏览器 CORS 规范：携带 Cookie/Authorization 头时，服务端必须精确声明允许的来源，`*` 会让浏览器拒绝该响应。

---

## 八、关键文件索引

| 文件 | 职责 |
|------|------|
| `config/SecurityConfig.java` | Spring Security 配置（CORS + 关闭一切） |
| `modules/identity/controller/AuthController.java` | 登录/登出/获取当前用户 |
| `modules/identity/interceptor/LoginInterceptor.java` | 鉴权拦截器（注解检查 + Sa-Token 校验 + 用户加载） |
| `modules/shared/annotation/LoginRequired.java` | 自定义"需要登录"注解 |
| `modules/shared/context/UserContext.java` | ThreadLocal 用户上下文 |
| `config/WebMvcConfig.java` | 注册拦截器到 Spring MVC |
| `config/CorsProperties.java` | CORS 允许的来源列表（配置文件可配） |

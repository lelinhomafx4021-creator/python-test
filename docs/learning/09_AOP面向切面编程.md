# 09 - AOP 面向切面编程深入

## 一、AOP 是什么

**不改原有代码，给方法"插一脚"。**

```
没有 AOP：
  方法A：日志 → 业务逻辑 → 日志
  方法B：日志 → 业务逻辑 → 日志
  方法C：日志 → 业务逻辑 → 日志
  （每个方法都要写日志代码，重复！）

有 AOP：
  统一织入：日志 → 方法A → 日志
           日志 → 方法B → 日志
           日志 → 方法C → 日志
  （方法里只写业务，日志由 AOP 统一处理）
```

## 二、核心概念

```
┌─────────────────────────────────────────────────┐
│                  AOP 核心概念                     │
├─────────────────────────────────────────────────┤
│                                                 │
│  切面（Aspect）= 要插入的逻辑（比如日志、鉴权）      │
│                                                 │
│  切入点（Pointcut）= 在哪里插入（哪些方法）          │
│                                                 │
│  通知（Advice）= 什么时候插入（之前/之后/环绕）      │
│                                                 │
│  连接点（JoinPoint）= 可能被插入的点（每个方法）     │
│                                                 │
│  织入（Weaving）= 把切面应用到目标方法的过程        │
│                                                 │
└─────────────────────────────────────────────────┘
```

用门卫比喻：

```
切面    = 门卫的工作职责（检查证件）
切入点  = 哪些房间需要检查（VIP 房间）
通知    = 什么时候检查（进门之前 / 出门之后 / 进出都检查）
连接点  = 所有的房间门（理论上都可以安排门卫）
织入    = 把门卫安排到门口的过程
```

## 三、五种通知类型

```java
@Aspect
@Component
public class LogAspect {

    // ① @Before — 方法执行前
    @Before("execution(* com.aiinvestor..service.*.*(..))")
    public void before(JoinPoint jp) {
        System.out.println("方法即将执行: " + jp.getSignature().getName());
    }

    // ② @After — 方法执行后（无论成功失败）
    @After("execution(* com.aiinvestor..service.*.*(..))")
    public void after(JoinPoint jp) {
        System.out.println("方法执行完毕: " + jp.getSignature().getName());
    }

    // ③ @AfterReturning — 方法正常返回后
    @AfterReturning(pointcut = "execution(* com.aiinvestor..service.*.*(..))", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        System.out.println("方法返回: " + result);
    }

    // ④ @AfterThrowing — 方法抛异常后
    @AfterThrowing(pointcut = "execution(* com.aiinvestor..service.*.*(..))", throwing = "ex")
    public void afterThrowing(JoinPoint jp, Exception ex) {
        System.out.println("方法抛异常: " + ex.getMessage());
    }

    // ⑤ @Around — 环绕通知（最强大，最常用）
    @Around("execution(* com.aiinvestor..service.*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("之前");
        Object result = pjp.proceed();  // 执行原方法
        System.out.println("之后");
        return result;
    }
}
```

## 五种通知的执行顺序

```
方法正常执行：
@Before → 方法 → @AfterReturning → @After

方法抛异常：
@Before → 方法 → @AfterThrowing → @After

@Around 包裹一切：
@Around 之前部分 → @Before → 方法 → @AfterReturning → @After → @Around 之后部分
```

## 四、切入点表达式（Pointcut）

告诉 AOP "拦截哪些方法"。

### execution 表达式

```
execution(修饰符? 返回类型 类名.方法名(参数) 异常?)

execution(* com.aiinvestor..service.*.*(..))
│        │  │              │     │ │  │
│        │  │              │     │ │  └─ 任意参数
│        │  │              │     │ └─ 任意方法名
│        │  │              │     └─ 任意类
│        │  │              └─ service 包
│        │  └─ 包路径（.. 表示任意子包）
│        └─ 任意返回类型
└─ execution 固定写法
```

### 常用写法

```java
// 拦截 service 包下所有方法
execution(* com.aiinvestor..service.*.*(..))

// 拦截某个具体方法
execution(* com.aiinvestor.gateway.modules.market.service.MarketService.getQuotes(..))

// 拦截所有 public 方法
execution(public * *(..))

// 拦截所有以 get 开头的方法
execution(* get*(..))

// 拦截所有 Controller
execution(* com.aiinvestor..controller.*.*(..))
```

### 其他切入点类型

```java
// @annotation — 拦截带某个注解的方法
@annotation(com.aiinvestor.gateway.modules.shared.annotation.LoginRequired)

// @within — 拦截带某个注解的类的所有方法
@within(org.springframework.stereotype.Service)

// args — 拦截参数类型匹配的方法
args(java.lang.String, ..)
```

## 五、@Around 环绕通知详解（最常用）

```java
@Around("execution(* com.aiinvestor..service.*.*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    // pjp = 被拦截的方法的所有信息

    // ① 之前（方法执行前）
    long start = System.currentTimeMillis();
    String methodName = pjp.getSignature().getName();
    Object[] args = pjp.getArgs();
    log.info("调用 {} 参数: {}", methodName, args);

    // ② 执行原方法（必须调用 proceed()，否则原方法不执行）
    Object result = pjp.proceed();

    // ③ 之后（方法执行后）
    long cost = System.currentTimeMillis() - start;
    log.info("调用 {} 返回: {} 耗时: {}ms", methodName, result, cost);

    return result;
}
```

### ProceedingJoinPoint 常用方法

```java
pjp.getSignature().getName()    // 方法名
pjp.getSignature().getDeclaringTypeName()  // 类名
pjp.getArgs()                   // 参数列表
pjp.proceed()                   // 执行原方法
pjp.proceed(newArgs)            // 用新参数执行原方法
pjp.getTarget()                 // 目标对象
```

### @Around 异常处理

```java
@Around("execution(* com.aiinvestor..service.*.*(..))")
public Object around(ProceedingJoinPoint pjp) {
    try {
        Object result = pjp.proceed();
        return result;
    } catch (Throwable e) {
        log.error("方法执行异常: {}", e.getMessage());
        throw new RuntimeException(e);  // 必须抛出去，否则异常被吞了
    }
}
```

## 六、实战案例

### 案例一：接口耗时统计

```java
@Aspect
@Component
@Slf4j
public class PerformanceAspect {

    @Around("execution(* com.aiinvestor..controller.*.*(..))")
    public Object measureTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();

        try {
            Object result = pjp.proceed();
            return result;
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 1000) {
                log.warn("[慢接口] {} 耗时 {}ms", method, cost);
            }
        }
    }
}
```

### 案例二：自定义注解 + AOP 实现操作日志

```java
// 第一步：定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String value();  // 操作描述
}

// 第二步：写切面
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Around("@annotation(operationLog)")
    public Object log(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        String desc = operationLog.value();  // 拿到注解里的描述
        String method = pjp.getSignature().getName();
        String user = UserContext.get().getUsername();

        log.info("[操作日志] 用户 {} 执行: {} - {}", user, method, desc);

        Object result = pjp.proceed();

        log.info("[操作日志] 用户 {} 完成: {} - {}", user, method, desc);
        return result;
    }
}

// 第三步：使用
@OperationLog("删除自选股分组")
@DeleteMapping("/watchlists/{id}")
public Result delete(@PathVariable Long id) {
    // 只写业务，日志由 AOP 自动记录
}
```

### 案例三：统一异常处理（AOP 版）

```java
@Aspect
@Component
@Slf4j
public class ExceptionAspect {

    @Around("execution(* com.aiinvestor..controller.*.*(..))")
    public Object handleException(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (BusinessException e) {
            log.warn("业务异常: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("系统异常: {}", e.getMessage(), e);
            return ApiResult.fail("系统繁忙，请稍后重试");
        }
    }
}
```

### 案例四：自定义注解实现缓存

```java
// 注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {
    String key();      // 缓存 key
    int ttl() default 300;  // 过期时间（秒）
}

// 切面
@Aspect
@Component
public class CacheAspect {

    @Autowired
    private RedisTemplate<String, Object> redis;

    @Around("@annotation(cacheable)")
    public Object cache(ProceedingJoinPoint pjp, Cacheable cacheable) throws Throwable {
        String key = cacheable.key();

        // 1. 先查缓存
        Object cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存没有，执行原方法
        Object result = pjp.proceed();

        // 3. 结果存入缓存
        redis.opsForValue().set(key, result, cacheable.ttl(), TimeUnit.SECONDS);
        return result;
    }
}

// 使用
@Cacheable(key = "stock:sectors", ttl = 60)
@GetMapping("/sectors")
public Result sectors() {
    // 第一次查数据库，后续 60 秒内走缓存
}
```

## 七、AOP 的实现原理

Spring AOP 用的是**动态代理**：

```
你写的类：MarketService
Spring 生成的代理类：MarketServiceProxy（继承 MarketService）

调用链：
Controller 调用 marketService.getQuotes()
    │
    ▼
实际调用的是 MarketServiceProxy.getQuotes()
    │
    ├─ @Before 通知
    ├─ 执行原方法 super.getQuotes()
    ├─ @AfterReturning 通知
    └─ 返回结果
```

两种代理方式：

| | JDK 动态代理 | CGLIB 代理 |
|---|---|---|
| 条件 | 目标类实现了接口 | 目标类没有接口 |
| 原理 | 创建接口的实现类 | 创建目标类的子类 |
| 限制 | 只能代理接口方法 | 不能代理 final 类/方法 |
| Spring 默认 | 有接口用这个 | 没接口用这个 |

## 八、AOP vs 拦截器 vs 过滤器

```
请求进来
  │
  ▼
Filter（过滤器）     ← Servlet 级别，最早执行
  │
  ▼
Interceptor（拦截器）← Spring MVC 级别，可以读注解
  │
  ▼
AOP（切面）          ← 方法级别，最精细
  │
  ▼
Controller 方法
```

| | Filter | Interceptor | AOP |
|---|---|---|---|
| 级别 | Servlet 容器 | Spring MVC | Spring Bean |
| 能拦截什么 | 所有请求（含静态资源） | Controller 请求 | 任何方法 |
| 能拿到什么 | Request/Response | HandlerMethod | 方法参数、返回值 |
| 典型场景 | 编码、CORS | 登录校验 | 日志、缓存、事务 |
| 注册方式 | `@WebFilter` / `FilterRegistrationBean` | `addInterceptors()` | `@Aspect` |

## 九、注意事项

### 1. AOP 不同类调用才生效

```java
@Service
public class UserService {
    public void a() {
        this.b();  // ❌ AOP 不生效！内部调用不走代理
    }

    @Cacheable(key = "user")
    public User b() { ... }
}

// 解决：注入自己
@Autowired
private UserService self;

public void a() {
    self.b();  // ✅ 走代理，AOP 生效
}
```

### 2. @Around 必须调用 proceed()

```java
@Around("...")
public Object around(ProceedingJoinPoint pjp) {
    // 忘了调 pjp.proceed() → 原方法不会执行！
    return pjp.proceed();  // 必须有这行
}
```

### 3. @Around 必须返回值

```java
@Around("...")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    return result;  // 必须返回，否则调用方拿到 null
}
```

## 十、一句话总结

- **AOP = 不改代码，给方法加功能**
- **@Before / @After = 前后通知**
- **@Around = 环绕通知，最强大，能控制是否执行原方法**
- **切入点表达式 = 告诉 AOP 拦截哪些方法**
- **自定义注解 + AOP = 最灵活的扩展方式**

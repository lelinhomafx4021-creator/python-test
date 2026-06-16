# 10 - Spring Security 系统学习

## 一、Spring Security 是什么

**Spring Security = Spring 生态的"安检系统"**

```
没有 Spring Security：
  每个接口都要手动检查登录、权限 → 代码重复、容易遗漏

有 Spring Security：
  请求自动经过过滤器链 → 认证、授权自动完成
```

**核心能力**：
- 认证（Authentication）：你是谁？（登录验证）
- 授权（Authorization）：你能做什么？（权限控制）
- 防护：CSRF、Session 固定攻击、点击劫持等

---

## 二、核心架构：过滤器链

Spring Security 的本质是**一堆过滤器，按顺序执行**。

```
请求进来
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Security 过滤器链                     │
├─────────────────────────────────────────────────────────┤
│  ① SecurityContextPersistenceFilter                     │
│     └─ 从 Session 获取/保存 SecurityContext（当前用户）   │
│                                                         │
│  ② UsernamePasswordAuthenticationFilter                 │
│     └─ 处理表单登录（POST /login）                       │
│                                                         │
│  ③ BasicAuthenticationFilter                            │
│     └─ 处理 HTTP Basic 认证                             │
│                                                         │
│  ④ ExceptionTranslationFilter                           │
│     └─ 处理认证/授权异常（401/403）                      │
│                                                         │
│  ⑤ FilterSecurityInterceptor                            │
│     └─ 最终的授权决策（能不能访问这个接口？）             │
└─────────────────────────────────────────────────────────┘
  │
  ▼
Controller 方法
```

**关键点**：
- 每个过滤器负责一件事（单一职责）
- 过滤器按顺序执行（顺序很重要）
- 可以自定义过滤器插入链中

---

## 三、两大核心：认证 + 授权

```
认证（Authentication）= 你是谁？
  用户名+密码 → 验证身份 → 生成 Authentication 对象

授权（Authorization）= 你能做什么？
  Authentication 对象 → 检查权限 → 允许/拒绝访问
```

**流程**：
```
用户登录 → 认证（验证身份）→ 授权（检查权限）→ 访问接口
```

---

## 四、认证流程详解

### 整体流程

```
用户提交用户名+密码
  │
  ▼
AuthenticationManager（认证管理器）
  │
  ▼
ProviderManager（提供者管理器）
  │
  ▼
DaoAuthenticationProvider（数据访问认证提供者）
  │
  ├─ ① UserDetailsService.loadUserByUsername()
  │     └─ 从数据库查用户，返回 UserDetails 对象
  │
  ├─ ② PasswordEncoder.matches()
  │     └─ 验证密码是否匹配
  │
  └─ ③ 认证成功 → 生成 Authentication 对象
        └─ 存入 SecurityContextHolder
```

### 关键接口

```java
// ① UserDetailsService — 加载用户（你需要实现）
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username);
}

// ② PasswordEncoder — 密码加密（Spring 提供实现）
public interface PasswordEncoder {
    String encode(CharSequence rawPassword);      // 加密
    boolean matches(CharSequence raw, String encoded);  // 验证
}

// ③ AuthenticationManager — 认证管理器（调用上面两个）
public interface AuthenticationManager {
    Authentication authenticate(Authentication authentication);
}
```

---

## 五、实战：实现 UserDetailsService

### 数据库表结构

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,  -- BCrypt 加密后
    role VARCHAR(20) DEFAULT 'USER',
    status INT DEFAULT 1  -- 1=正常, 0=禁用
);
```

### 实现 UserDetailsService

```java
@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查用户
        UserDO user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 返回 UserDetails
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())  // 数据库里是加密后的密码
                .roles(user.getRole())         // 角色（如 "ADMIN", "USER"）
                .disabled(user.getStatus() != 1)  // 是否禁用
                .build();
    }
}
```

### 自定义 UserDetails（更灵活）

```java
@Data
public class MyUserDetails implements UserDetails {

    private Long id;
    private String username;
    private String password;
    private String role;
    private Integer status;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 返回角色列表（ROLE_ 前缀是 Spring Security 的约定）
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;  // 账户未过期
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == 1;  // 状态为 1 表示正常
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;  // 密码未过期
    }

    @Override
    public boolean isEnabled() {
        return status == 1;  // 状态为 1 表示启用
    }
}
```

---

## 六、密码加密（BCrypt）

### 为什么用 BCrypt？

```
明文存储：password = "123456" → 数据库泄露 → 密码直接暴露
MD5：     password = "e10adc3949..." → 可以被彩虹表破解
BCrypt：  password = "$2a$10$N9qo8uLO..." → 每次加密结果不同，无法破解
```

### 使用方式

```java
@Configuration
public class SecurityConfig {

    // 注册密码加密器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// 注入使用
@Service
public class UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 注册时：加密密码
    public void register(String username, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        // 存入数据库
        userMapper.insert(username, encodedPassword);
    }

    // 登录验证：Spring Security 自动调用
    // passwordEncoder.matches(用户输入, 数据库存储)
}
```

### BCrypt 特点

| 特点 | 说明 |
|------|------|
| 每次加密结果不同 | 加了随机盐，同一个密码每次加密结果不一样 |
| 不可逆 | 不能从加密密码反推原始密码 |
| 安全性高 | 目前没有有效破解方式 |

---

## 七、授权方式

### 方式一：配置类（URL 级别）

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            // 公开接口（不需要登录）
            .requestMatchers("/api/public/**", "/login", "/register").permitAll()

            // 需要登录
            .requestMatchers("/api/user/**").authenticated()

            // 需要 ADMIN 角色
            .requestMatchers("/api/admin/**").hasRole("ADMIN")

            // 需要任意一个角色
            .requestMatchers("/api/manager/**").hasAnyRole("ADMIN", "MANAGER")

            // 需要具体权限
            .requestMatchers(HttpMethod.DELETE, "/api/**").hasAuthority("user:delete")

            // 其他请求需要登录
            .anyRequest().authenticated()
        );

    return http.build();
}
```

### 方式二：注解（方法级别）

```java
// 开启注解支持
@Configuration
@EnableMethodSecurity
public class SecurityConfig { }

// 使用注解
@RestController
@RequestMapping("/users")
public class UserController {

    // 需要登录
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/profile")
    public Result profile() { }

    // 需要 ADMIN 角色
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) { }

    // 需要具体权限
    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) { }

    // SpEL 表达式（灵活）
    @PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")
    @GetMapping("/{userId}")
    public Result getUser(@PathVariable Long userId) { }

    // 登录后才能访问，且只能访问自己的数据
    @PostAuthorize("returnObject.username == authentication.name")
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) { }
}
```

### 方式三：编程式（代码里判断）

```java
@Service
public class UserService {

    // 方式 A：通过 SecurityContextHolder
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    // 方式 B：通过权限检查
    public boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }

    // 方式 C：手动检查权限（不通过则抛异常）
    public void checkAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("权限不足");
        }
    }
}
```

---

## 八、SecurityContextHolder（核心）

### 是什么

**SecurityContextHolder = 存储当前用户信息的容器**（用 ThreadLocal 实现）

```java
// 获取当前登录用户
Authentication auth = SecurityContextHolder.getContext().getAuthentication();

String username = auth.getName();           // 用户名
Object principal = auth.getPrincipal();     // UserDetails 对象
Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();  // 权限列表
boolean isAuthenticated = auth.isAuthenticated();  // 是否已认证
```

### 为什么用 ThreadLocal？

```
请求 1 → 线程 1 → SecurityContext 存用户 A
请求 2 → 线程 2 → SecurityContext 存用户 B

每个线程独立，互不干扰
```

### 自动清理

```java
// 请求结束后，SecurityContext 自动清理
// 因为 SecurityContextPersistenceFilter 会在请求结束时清理
```

---

## 九、完整配置示例

### 基础配置（表单登录）

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private MyUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF（前后端分离不需要）
            .csrf(AbstractHttpConfigurer::disable)

            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // 配置表单登录
            .formLogin(form -> form
                .loginPage("/login")              // 自定义登录页
                .loginProcessingUrl("/doLogin")   // 登录提交 URL
                .usernameParameter("username")    // 用户名参数名
                .passwordParameter("password")    // 密码参数名
                .successHandler((req, res, auth) -> {
                    // 登录成功处理
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":200,\"msg\":\"登录成功\"}");
                })
                .failureHandler((req, res, e) -> {
                    // 登录失败处理
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":401,\"msg\":\"登录失败: " + e.getMessage() + "\"}");
                })
            )

            // 配置登出
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((req, res, auth) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":200,\"msg\":\"登出成功\"}");
                })
            )

            // 配置异常处理
            .exceptionHandling(ex -> ex
                // 未登录
                .authenticationEntryPoint((req, res, e) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(401);
                    res.getWriter().write("{\"code\":401,\"msg\":\"未登录\"}");
                })
                // 无权限
                .accessDeniedHandler((req, res, e) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(403);
                    res.getWriter().write("{\"code\":403,\"msg\":\"权限不足\"}");
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### JWT 配置（无状态）

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // 无状态（不用 Session）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register").permitAll()
                .anyRequest().authenticated()
            )
            // 添加 JWT 过滤器
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## 十、常用注解

| 注解 | 作用 | 示例 |
|------|------|------|
| `@PreAuthorize` | 方法执行前检查权限 | `@PreAuthorize("hasRole('ADMIN')")` |
| `@PostAuthorize` | 方法执行后检查权限 | `@PostAuthorize("returnObject.username == authentication.name")` |
| `@PreFilter` | 方法执行前过滤参数 | `@PreFilter("filterObject.owner == authentication.name")` |
| `@PostFilter` | 方法执行后过滤返回值 | `@PostFilter("filterObject.owner == authentication.name")` |
| `@Secured` | 简单角色检查（已过时） | `@Secured("ROLE_ADMIN")` |
| `@RolesAllowed` | JSR-250 标准 | `@RolesAllowed("ADMIN")` |

### SpEL 表达式

```java
// 角色检查
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@PreAuthorize("hasAuthority('user:delete')")

// 登录状态
@PreAuthorize("isAuthenticated()")
@PreAuthorize("isAnonymous()")

// 方法参数
@PreAuthorize("#id == authentication.principal.id")
@PreAuthorize("#user.username == authentication.name")

// 复杂表达式
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
```

---

## 十一、异常处理

### 常见异常

| 异常 | 含义 | HTTP 状态码 |
|------|------|-------------|
| `BadCredentialsException` | 密码错误 | 401 |
| `UsernameNotFoundException` | 用户不存在 | 401 |
| `DisabledException` | 账户已禁用 | 401 |
| `LockedException` | 账户已锁定 | 401 |
| `AccessDeniedException` | 权限不足 | 403 |
| `AuthenticationCredentialsNotFoundException` | 未登录 | 401 |

### 全局异常处理

```java
@RestControllerAdvice
public class SecurityExceptionHandler {

    // 未登录
    @ExceptionHandler(AuthenticationException.class)
    public Result handleAuthException(AuthenticationException e) {
        return Result.fail(401, "未登录: " + e.getMessage());
    }

    // 权限不足
    @ExceptionHandler(AccessDeniedException.class)
    public Result handleAccessDenied(AccessDeniedException e) {
        return Result.fail(403, "权限不足");
    }
}
```

---

## 十二、面试常见问题

### Q1：Spring Security 的认证流程？

```
用户提交用户名+密码
  → AuthenticationManager
  → ProviderManager
  → DaoAuthenticationProvider
  → UserDetailsService.loadUserByUsername()（查数据库）
  → PasswordEncoder.matches()（验证密码）
  → 认证成功，生成 Authentication 对象
  → 存入 SecurityContextHolder
```

### Q2：Spring Security 的授权方式？

1. **配置类**：`requestMatchers("/admin/**").hasRole("ADMIN")`
2. **注解**：`@PreAuthorize("hasRole('ADMIN')")`
3. **编程式**：`SecurityContextHolder.getContext().getAuthentication()`

### Q3：BCrypt 和 MD5 的区别？

| | MD5 | BCrypt |
|---|---|---|
| 加密结果 | 固定 | 每次不同（加盐） |
| 可逆性 | 可破解（彩虹表） | 不可逆 |
| 安全性 | 低 | 高 |
| 推荐 | ❌ 不推荐 | ✅ 推荐 |

### Q4：为什么关闭 CSRF？

```
CSRF 攻击：用户已登录 → 访问恶意网站 → 恶意网站冒充用户发请求

为什么关闭：
  1. 前后端分离：前端通过 Token 鉴权，不依赖 Cookie
  2. Token 本身就有 CSRF 防护能力
  3. 有 CORS 限制跨域访问
```

### Q5：SecurityContextHolder 用什么存储？

**ThreadLocal**：每个请求线程独立，互不干扰。

---

## 十三、一句话总结

| 概念 | 作用 |
|------|------|
| **FilterChain** | 过滤器链，请求经过的一系列检查 |
| **Authentication** | 认证：你是谁？（用户名+密码验证） |
| **Authorization** | 授权：你能做什么？（角色/权限检查） |
| **UserDetailsService** | 从数据库加载用户（你实现这个接口） |
| **PasswordEncoder** | 密码加密（BCrypt） |
| **SecurityContextHolder** | 存储当前用户（ThreadLocal） |
| **@PreAuthorize** | 方法级权限注解 |

**Spring Security = 认证（你是谁）+ 授权（你能做什么）+ 过滤器链（执行顺序）。**

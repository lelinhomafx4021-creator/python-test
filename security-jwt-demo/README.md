# Spring Security + JWT 最简示例

## 项目结构

```
security-jwt-demo/
├── src/main/java/com/example/security/
│   ├── SecurityJwtDemoApplication.java  # 启动类
│   ├── config/
│   │   └── SecurityConfig.java          # Security 配置
│   ├── controller/
│   │   ├── AuthController.java          # 登录接口
│   │   ├── DemoController.java          # 测试接口
│   │   ├── LoginRequest.java            # 登录请求 DTO
│   │   └── Result.java                  # 返回结果 DTO
│   ├── entity/
│   │   └── User.java                    # 用户实体
│   ├── filter/
│   │   └── JwtAuthenticationFilter.java # JWT 过滤器
│   ├── service/
│   │   └── MyUserDetailsService.java    # 用户DetailsService
│   └── util/
│       ├── JwtUtils.java                # JWT 工具类
│       └── PasswordGenerator.java       # 密码生成工具
└── src/main/resources/
    └── application.yml                  # 配置文件
```

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| zhangsan | 123456 | ADMIN |
| lisi | 123456 | USER |

## 启动项目

```bash
cd security-jwt-demo
mvn spring-boot:run
```

项目运行在 http://localhost:9090

## 测试接口

### 1. 公开接口（不需要登录）

```bash
curl http://localhost:9090/api/public/hello
```

### 2. 登录获取 Token

```bash
# zhangsan 登录（ADMIN）
curl -X POST http://localhost:9090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"123456"}'

# lisi 登录（USER）
curl -X POST http://localhost:9090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"lisi","password":"123456"}'
```

返回：
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 3. 带 Token 访问接口

```bash
# 替换 <token> 为上一步获取的 token
curl http://localhost:9090/api/user/info \
  -H "Authorization: Bearer <token>"
```

### 4. 测试权限控制

```bash
# 用 zhangsan (ADMIN) 的 token 访问管理员接口 ✅
curl http://localhost:9090/api/admin/users \
  -H "Authorization: Bearer <zhangsan的token>"

# 用 lisi (USER) 的 token 访问管理员接口 ❌ 403
curl http://localhost:9090/api/admin/users \
  -H "Authorization: Bearer <lisi的token>"

# 用 lisi (USER) 的 token 访问用户接口 ✅
curl http://localhost:9090/api/user/profile \
  -H "Authorization: Bearer <lisi的token>"
```

## 核心流程

```
登录：
  POST /api/auth/login {username, password}
    → AuthenticationManager.authenticate()
    → UserDetailsService.loadUserByUsername() 查用户
    → PasswordEncoder.matches() 验证密码
    → 生成 JWT Token
    → 返回 {token: "eyJhbG..."}

访问接口：
  GET /api/user/info
    Header: Authorization: Bearer eyJhbG...
    → JwtAuthenticationFilter 拦截
    → 解析 Token → 拿到 userId, username, role
    → 构建 Authentication → 存入 SecurityContextHolder
    → 检查权限 → 放行/拒绝
```

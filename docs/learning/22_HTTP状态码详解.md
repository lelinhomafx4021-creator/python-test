# 22 - HTTP 状态码：谁在控制？从哪来的？

> 你问的核心问题是：HTTP 状态码到底是谁写的？前端没请求到后端（比如后端没开），500/502 是哪来的？这篇从请求的完整链路讲清楚。

## 一、一条请求的完整链路

前端发一个请求，要经过好几层，每一层都可能生成 HTTP 状态码：

```
浏览器 → Nginx → Tomcat/Uvicorn → Spring/FastAPI → 你的代码
  ①         ②          ③              ④              ⑤

每一层都可能"截胡"，直接返回状态码，请求到不了下一层。
```

```
┌─────────────────────────────────────────────────────────────┐
│  第①层：浏览器 / 网络层                                       │
│  后端根本没开 → 连接被拒绝 → 根本没有 HTTP 响应                  │
│  浏览器显示：ERR_CONNECTION_REFUSED（不是 HTTP 状态码！）        │
├─────────────────────────────────────────────────────────────┤
│  第②层：Nginx / 反向代理                                      │
│  Nginx 转发给后端，后端没响应 → Nginx 返回 502 Bad Gateway      │
│  Nginx 转发超时 → Nginx 返回 504 Gateway Timeout              │
├─────────────────────────────────────────────────────────────┤
│  第③层：Web 服务器（Tomcat / Uvicorn）                         │
│  URL 路径不存在任何路由 → 404 Not Found                        │
│  请求体太大 → 413 Payload Too Large                           │
│  代码崩了没 catch → 500 Internal Server Error                 │
├─────────────────────────────────────────────────────────────┤
│  第④层：框架层（Spring / FastAPI）                              │
│  参数校验失败 → 422 Unprocessable Entity（FastAPI 默认）        │
│  请求方法不对 → 405 Method Not Allowed                        │
├─────────────────────────────────────────────────────────────┤
│  第⑤层：你的代码                                              │
│  你主动设置的任何状态码                                         │
│  raise HTTPException(404) / @ResponseStatus / ApiResult       │
└─────────────────────────────────────────────────────────────┘
```

**关键理解**：HTTP 状态码不是只有"你的代码"在写，每一层都可能生成。

## 二、第①层：后端没开，根本没有 HTTP

```
浏览器 → 尝试连接 127.0.0.1:8080 → TCP 连接被拒绝

结果：没有 HTTP 响应，没有状态码
浏览器显示：ERR_CONNECTION_REFUSED / 网络错误
前端 fetch() 抛异常：TypeError: Failed to fetch
```

```javascript
// 前端代码
try {
    const resp = await fetch('http://127.0.0.1:8080/api/students');
} catch (e) {
    // 走到这里，e 是 TypeError，不是 HTTP 错误
    // resp 根本不存在，拿不到 status
    console.log('网络错误，后端可能没开');
}
```

**这不是 HTTP 状态码，是 TCP 层面的连接失败。** HTTP 协议都没建立起来。

## 三、第②层：Nginx 生成的状态码

生产环境通常有 Nginx 做反向代理：

```
浏览器 → Nginx (80/443) → 后端 (8080)
```

Nginx 会生成这些状态码：

| 状态码 | 含义 | 场景 |
|--------|------|------|
| 502 | Bad Gateway | Nginx 转发给后端，后端返回了非法响应 |
| 504 | Gateway Timeout | Nginx 转发给后端，后端超时没响应 |
| 403 | Forbidden | Nginx 层面的权限控制（IP 黑名单等） |
| 301/302 | Redirect | Nginx 配置的 URL 重定向 |

```nginx
# Nginx 配置示例
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_read_timeout 30s;   # 超过 30 秒没响应 → 504
}
```

**这些状态码和你的 Java/Python 代码完全无关，是 Nginx 自己生成的。**

## 四、第③④层：框架默认行为

### Java（Spring Boot + Tomcat）

请求进入 Tomcat → Spring MVC 路由 → 你的 Controller

```java
// 场景 1：访问一个不存在的 URL（如 /api/xxx）
// Spring 找不到对应的 @RequestMapping → 返回 404
// 你没写任何代码，框架自动处理

// 场景 2：参数类型不对（传了字符串给 int 参数）
// Spring 的参数解析器报错 → 返回 400
// 你没写任何代码，框架自动处理

// 场景 3：Controller 里抛了未捕获的异常
// 如果没有 GlobalExceptionHandler → Tomcat 返回 500 + 默认错误页面
// 如果有 GlobalExceptionHandler → 你的处理器接管
```

Spring Boot 的默认错误处理：

```java
// 你什么都不写，Spring Boot 有一个默认的 /error 端点
// 未捕获异常 → 转发到 /error → 返回 Whitelabel Error Page
// HTTP 状态码由异常类型决定：
//   HttpRequestMethodNotSupportedException → 405
//   HttpMessageNotReadableException       → 400
//   MethodArgumentNotValidException       → 400
//   其他 Exception                        → 500
```

### Python（FastAPI + Uvicorn）

```python
# 场景 1：访问不存在的 URL
# FastAPI 没有注册这个路由 → 返回 404 {"detail":"Not Found"}
# 你没写任何代码，框架自动处理

# 场景 2：请求体 JSON 格式错误
# FastAPI 解析失败 → 返回 422 {"detail":[{"type":"json_invalid",...}]}
# 你没写任何代码，框架自动处理

# 场景 3：路由函数里抛了未捕获的异常
# 如果没有 exception_handler → Uvicorn 返回 500 Internal Server Error
# 如果有 exception_handler → 你的处理器接管
```

## 五、第⑤层：你的代码怎么控制

### Java 的三种方式

**方式一：注解声明（最常用）**

```java
// 注解直接声明这个方法返回什么状态码
@PostMapping("/students")
@ResponseStatus(HttpStatus.CREATED)          // 成功时返回 201
public ApiResult<Student> createStudent(@RequestBody StudentCreate req) {
    return ApiResult.ok(studentService.create(req));
}

@DeleteMapping("/students/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)       // 成功时返回 204
public void deleteStudent(@PathVariable Long id) {
    studentService.delete(id);
}
```

**方式二：ResponseEntity（灵活控制）**

```java
// ResponseEntity 可以同时控制状态码 + 响应头 + 响应体
@GetMapping("/students/{id}")
public ResponseEntity<ApiResult<Student>> getStudent(@PathVariable Long id) {
    Student student = studentService.findById(id);
    if (student == null) {
        // 手动设置 404
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.fail(404, "学生不存在"));
    }
    // 手动设置 200
    return ResponseEntity.ok(ApiResult.ok(student));
}
```

**方式三：抛异常 + GlobalExceptionHandler 拦截**

```java
// Service 层抛异常
public Student findById(Long id) {
    Student student = studentRepo.findById(id);
    if (student == null) {
        throw new BusinessException("学生不存在");
        // 不在这里设置状态码，交给 GlobalExceptionHandler
    }
    return student;
}

// GlobalExceptionHandler 统一处理
@ExceptionHandler(BusinessException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)        // 这里设置状态码
public ApiResult<Void> handleBusiness(BusinessException e) {
    return ApiResult.fail(400, e.getMessage());
}
```

**拦截器能不能设置状态码？能。**

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token == null) {
            response.setStatus(401);           // 直接设置 HTTP 状态码
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return false;                      // 返回 false = 不继续执行 Controller
        }
        return true;                           // 返回 true = 继续执行
    }
}

// 拦截器的执行顺序：
// 请求 → Filter → Interceptor → Controller
// 拦截器 return false → Controller 根本不执行，直接返回
```

### Python（FastAPI）的方式

**方式一：raise HTTPException**

```python
@router.get("/{student_id}")
def get_student(student_id: int, db: Session = Depends(get_session)):
    student = db.get(Student, student_id)
    if not student:
        raise HTTPException(status_code=404, detail="学生不存在")
    return student
```

**方式二：直接返回 Response 对象**

```python
from fastapi.responses import JSONResponse

@router.get("/{student_id}")
def get_student(student_id: int, db: Session = Depends(get_session)):
    student = db.get(Student, student_id)
    if not student:
        return JSONResponse(
            status_code=404,                    # 手动设置状态码
            content={"detail": "学生不存在"},
        )
    return student
```

**方式三：中间件拦截（等价于 Java 拦截器）**

```python
@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    # 中间件可以截胡，直接返回，不走路由函数
    if request.url.path.startswith("/admin"):
        token = request.headers.get("Authorization")
        if not token:
            return JSONResponse(
                status_code=401,                # 直接返回 401
                content={"detail": "未登录"},
            )
    response = await call_next(request)         # 继续走后续逻辑
    return response
```

## 六、完整流程图：一个请求从头到尾

```
前端 fetch('/api/students/999')
         │
         ▼
    ┌─ 第①层：网络 ─────────────────────────────┐
    │  后端没开？                                  │
    │  → TCP 连接失败，没有 HTTP 响应              │
    │  → 前端 catch(TypeError)                   │
    │  → 结束                                     │
    └────────────────────────────────────────────┘
         │ 后端在运行
         ▼
    ┌─ 第②层：Nginx ─────────────────────────────┐
    │  后端超时？                                   │
    │  → Nginx 返回 504 Gateway Timeout           │
    │  → 结束                                     │
    └────────────────────────────────────────────┘
         │ 正常转发
         ▼
    ┌─ 第③层：Tomcat/Uvicorn ────────────────────┐
    │  URL 格式非法？                              │
    │  → 400 Bad Request                         │
    └────────────────────────────────────────────┘
         │
         ▼
    ┌─ 第④层：Spring/FastAPI ────────────────────┐
    │  URL 没有匹配的路由？                         │
    │  → 404 Not Found（框架默认）                 │
    │  参数类型不对？                               │
    │  → 422（FastAPI）/ 400（Spring）             │
    └────────────────────────────────────────────┘
         │ 路由匹配成功
         ▼
    ┌─ 第⑤层：你的代码 ──────────────────────────┐
    │                                              │
    │  拦截器/中间件检查：                           │
    │    没 token → return JSONResponse(401)       │
    │    有 token → 继续                           │
    │                                              │
    │  Controller/路由函数执行：                     │
    │    数据不存在 → raise HTTPException(404)     │
    │    数据存在   → return 数据（默认 200）        │
    │                                              │
    │  异常处理器兜底：                              │
    │    未捕获异常 → GlobalExceptionHandler        │
    │    → 返回 500 或 ApiResult.fail(500)         │
    └────────────────────────────────────────────┘
         │
         ▼
    前端收到响应
```

## 七、对照表：谁生成了什么

| 状态码 | 谁生成的 | 你的代码能控制吗 |
|--------|---------|----------------|
| 无状态码（连接失败） | 操作系统/网络层 | 不能，后端没开 |
| 502 Bad Gateway | Nginx | 不能，但可以修 Nginx 配置 |
| 504 Gateway Timeout | Nginx | 不能，但可以调超时时间 |
| 404 Not Found（框架默认） | Spring/FastAPI 路由 | 能，注册路由或自定义 404 页面 |
| 422 参数校验失败 | FastAPI 参数解析器 | 能，自定义 exception_handler |
| 400 参数错误 | Spring 参数解析器 | 能，自定义 exception_handler |
| 500 未捕获异常 | Tomcat/Uvicorn | 能，加 GlobalExceptionHandler |
| 401/403/404/201 | 你的代码 | 完全由你控制 |

## 30 秒电梯演讲

HTTP 状态码有 5 层来源：① 网络层（连接失败，没有 HTTP）、② Nginx（502/504）、③ Web 服务器（默认 404/500）、④ 框架层（422/400）、⑤ 你的代码（任意）。后端没开时前端看到的不是 HTTP 状态码，是 TCP 连接失败。拦截器/中间件可以直接设置状态码并截断请求，不需要走到 Controller。

## 面试速记

| 问题 | 答案 |
|------|------|
| 502 和 504 区别？ | 502=后端返回非法响应，504=后端超时没响应 |
| 后端没开，前端收到什么状态码？ | 没有 HTTP 状态码，是 TCP 连接失败 |
| 拦截器能设置状态码吗？ | 能，response.setStatus(401)，return false 截断请求 |
| 500 是谁生成的？ | 没有 GlobalExceptionHandler 时由 Tomcat/Uvicorn 默认生成 |
| FastAPI 的 422 是哪来的？ | 框架参数解析器自动生成，不是你写的 |

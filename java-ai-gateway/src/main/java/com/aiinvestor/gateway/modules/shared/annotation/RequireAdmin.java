package com.aiinvestor.gateway.modules.shared.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ============================================================
 * 自定义注解：标记"需要管理员角色才能访问"
 * ============================================================
 *
 * 设计思路：
 *   与 @LoginRequired 同一模式——注解只做标记，真正的校验在
 *   LoginInterceptor 中完成。这样可以统一管理鉴权逻辑，避免
 *   在每个 Controller 中重复写角色判断代码。
 *
 * 执行顺序：
 *   @LoginRequired 先校验登录态 → @RequireAdmin 再校验角色
 *
 * 使用方式：
 *   方法级别：
 *     @RequireAdmin
 *     @DeleteMapping("/announcements/{id}")
 *     public ApiResult<?> delete() { ... }
 *
 *   类级别（整个 Controller 都需要管理员）：
 *     @RequireAdmin
 *     @RestController
 *     public class AdminController { ... }
 *
 * 注解元信息：
 *   - @Target({METHOD, TYPE}) : 可标注在方法上和类/接口上
 *   - @Retention(RUNTIME)     : 运行时保留，反射可读
 *   - @Documented             : 生成 Javadoc 时包含此注解
 *
 * @see com.aiinvestor.gateway.modules.identity.interceptor.LoginInterceptor
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
    // 标记型注解，无需定义属性
}

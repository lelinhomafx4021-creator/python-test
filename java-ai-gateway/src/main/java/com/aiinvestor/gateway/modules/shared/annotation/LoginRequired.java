package com.aiinvestor.gateway.modules.shared.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ============================================================
 * 自定义注解：标记"此接口需要登录才能访问"
 * ============================================================
 *
 * 设计思路（AOP 思想）：
 *   这个注解本身不执行任何校验逻辑，它只是一个"标签"。
 *   真正的校验由 LoginInterceptor 在请求进入 Controller 之前完成。
 *
 * 使用方式：
 *   方法级别：
 *     @LoginRequired
 *     @GetMapping("/me")
 *     public ApiResult<?> me() { ... }
 *
 *   类级别（整个 Controller 都需要登录）：
 *     @LoginRequired
 *     @RestController
 *     public class AiGatewayController { ... }
 *
 * 注解元信息解读：
 *   - @Target({METHOD, TYPE}) : 可以标注在方法上和类/接口上
 *   - @Retention(RUNTIME)     : 注解信息保留到运行时（这样才能被反射读取）
 *   - @Documented             : 生成 Javadoc 时会包含此注解
 *
 * @author AI Investor Team
 */
@Target({ElementType.METHOD, ElementType.TYPE})   // 可用于方法或类
@Retention(RetentionPolicy.RUNTIME)               // 运行时保留（反射可用）
@Documented                                        // Javadoc 可见
public @interface LoginRequired {
    // 标记型注解，无需定义属性
}

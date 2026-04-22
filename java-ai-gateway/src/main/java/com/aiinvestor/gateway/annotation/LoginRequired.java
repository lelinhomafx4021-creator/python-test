package com.aiinvestor.gateway.annotation;

import java.lang.annotation.*;

/**
 * 【企业级标准】登录校验注解
 * 
 * 知识点 (面试必问)：
 * 1. 为什么用注解？ 
 *    - 实现了权限校验的“组件化”。只需要在 Controller 或方法上打个标，就能自动校验，不侵入业务代码（AOP 思想）。
 * 2. 这里的 RetentionPolicy.RUNTIME 作用？
 *    - 保证在运行时通过反射可以读到这个注解，拦截器才能生效。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginRequired {
}

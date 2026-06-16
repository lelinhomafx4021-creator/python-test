package com.aiinvestor.gateway.modules.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * 标记在 Controller 方法上，AOP 会自动记录操作日志。
 *
 * 使用方式：
 *   @OperationLog("删除自选股分组")
 *   @DeleteMapping("/watchlists/{id}")
 *   public Result delete(@PathVariable Long id) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String value();  // 操作描述
}

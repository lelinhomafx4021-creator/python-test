package com.aiinvestor.gateway.modules.shared.aspect;

import com.aiinvestor.gateway.modules.shared.annotation.OperationLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面。
 * 拦截带 @OperationLog 注解的方法，自动记录操作日志。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Around("@annotation(operationLog)")
    public Object log(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        String desc = operationLog.value();
        String method = pjp.getSignature().getName();

        log.info("[操作日志] 开始: {} - {}", method, desc);

        try {
            Object result = pjp.proceed();
            log.info("[操作日志] 成功: {} - {}", method, desc);
            return result;
        } catch (Throwable e) {
            log.error("[操作日志] 失败: {} - {} - {}", method, desc, e.getMessage());
            throw e;
        }
    }
}

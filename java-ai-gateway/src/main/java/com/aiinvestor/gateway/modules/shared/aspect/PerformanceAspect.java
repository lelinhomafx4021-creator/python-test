package com.aiinvestor.gateway.modules.shared.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 接口耗时统计切面。
 * 监控所有 Controller 方法的执行时间，超过 1 秒记录告警。
 */
@Slf4j
@Aspect
@Component
public class PerformanceAspect {

    @Around("execution(* com.aiinvestor..controller.*.*(..))")
    public Object measureTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();

        try {
            return pjp.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 1000) {
                log.warn("[慢接口] {} 耗时 {}ms", method, cost);
            }
        }
    }
}

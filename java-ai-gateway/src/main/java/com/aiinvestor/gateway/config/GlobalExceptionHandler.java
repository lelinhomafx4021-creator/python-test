package com.aiinvestor.gateway.config;

import com.aiinvestor.gateway.model.vo.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：统一拦截 Controller 抛出的异常
 * 
 * 面试加分点：
 * 1. 同一格式返回：无论是业务错误还是服务器崩溃，前端接收到的 JSON 格式永远一致。
 * 2. 信息掩蔽：避免将程序的报错堆栈（Stack Trace）直接暴露给用户，防止安全漏洞。
 */
@Slf4j
@RestControllerAdvice // 这是一个组合注解，表示此类是 Rest 处理器的切面，拦截异常并返回 JSON
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常（Spring Validation）
     * 比如：字段不能为空，或者格式不正确
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 提取所有的验证错误信息，拼成一个字符串返回
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ApiResult.fail(400, "参数校验失败: " + msg);
    }

    /**
     * 拦截所有未定义的运行时异常（兜底处理）
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        // 在服务器日志里把错误打印出来，方便程序员排查（但千万别返回给用户看）
        log.error("系统运行出错，发生了未捕获异常: ", e);
        return ApiResult.fail(500, "小助手有点累了，请稍后再试（服务器内部错误）");
    }
}

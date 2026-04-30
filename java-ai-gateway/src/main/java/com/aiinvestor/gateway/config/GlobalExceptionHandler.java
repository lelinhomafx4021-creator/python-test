package com.aiinvestor.gateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * ============================================================
 * 全局异常处理器 - 统一拦截 Controller 抛出的所有异常
 * ============================================================
 *
 * 设计目标（面试加分项）：
 *   1. 统一响应格式：不管是业务错误还是系统崩溃，前端收到的 JSON 结构永远一致
 *   2. 信息安全：绝不把 Java 异常堆栈（Stack Trace）暴露给前端，防止攻击者
 *      通过报错信息推断系统内部实现
 *   3. 代码复用：每个 Controller 不用自己写 try-catch，异常全在这里统一处理
 *
 * 技术原理：
 *   @RestControllerAdvice 是 @ControllerAdvice + @ResponseBody 的组合，
 *   它会拦截所有 @RestController 中抛出的异常，并返回 JSON 而非错误页面。
 *
 * @author AI Investor Team
 */
@Slf4j                                              // Lombok：自动生成 log 对象
@RestControllerAdvice                                // 全局异常拦截 + JSON 返回
public class GlobalExceptionHandler {

    /**
     * 处理"未登录"异常。
     *
     * 触发场景：
     *   - 前端不带 token 访问需要登录的接口
     *   - token 过期
     *   - Sa-Token 校验失败
     *
     * @param e NotLoginException 异常对象
     * @return 401 状态码 + 错误提示
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)          // HTTP 401
    public ApiResult<Void> handleNotLoginException(NotLoginException e) {
        return ApiResult.fail(401, "请先登录后再继续操作");
    }

    /**
     * 处理"参数校验失败"异常。
     *
     * 触发场景：
     *   前端请求中某个字段违反了 @NotBlank / @Min / @Max 等校验规则。
     *   例如：message 为空、session_id 为空等。
     *
     * 处理方式：
     *   把所有的校验错误信息拼成一条可读的字符串返回，
     *   方便前端直接展示给用户（如："消息内容不能为空, 会话ID不能为空"）。
     *
     * @param e MethodArgumentNotValidException 异常对象
     * @return 400 状态码 + 校验错误详情
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 从 BindingResult 中提取所有字段的校验失败信息
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ApiResult.fail(400, "参数校验失败: " + msg);
    }

    /**
     * 兜底处理：拦截所有未被上面特定 handler 捕获的异常。
     *
     * 安全原则：
     *   - 服务器日志中记录完整堆栈（log.error），方便开发者排查
     *   - 返回给前端的信息必须是模糊的通用提示，绝不暴露内部错误细节
     *
     * @param e Exception 异常对象（任意类型）
     * @return 500 状态码 + 用户友好的错误提示
     */
    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        return ApiResult.fail(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        // 完整堆栈只记录在服务器日志中
        log.error("系统运行出错，发生了未捕获异常: ", e);
        // 返回给前端的必须是"无害"的通用提示
        return ApiResult.fail(500, "小助手有点累了，请稍后再试（服务器内部错误）");
    }
}

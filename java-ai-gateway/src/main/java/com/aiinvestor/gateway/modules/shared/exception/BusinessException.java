package com.aiinvestor.gateway.modules.shared.exception;

/**
 * 业务异常。
 * <p>
 * 用于表达"请求合法，但业务条件不满足"的场景，
 * 例如配额不足、持仓不足、重复提交、权限不够等。
 * <p>
 * 由 {@link com.aiinvestor.gateway.config.GlobalExceptionHandler} 统一拦截，
 * 转换为 HTTP 400 + 统一 JSON 错误响应，不会打印堆栈到日志（区别于系统异常）。
 */
public class BusinessException extends RuntimeException {

    /**
     * @param message 业务错误描述（会直接展示给前端用户）
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * @param message 业务错误描述
     * @param cause   原始异常（用于日志追溯）
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

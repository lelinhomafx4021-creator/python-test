package com.aiinvestor.gateway.modules.shared.exception;

/**
 * 业务异常。
 * 用于表达“请求合法，但业务条件不满足”的场景，
 * 例如配额不足、持仓不足、重复提交等。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

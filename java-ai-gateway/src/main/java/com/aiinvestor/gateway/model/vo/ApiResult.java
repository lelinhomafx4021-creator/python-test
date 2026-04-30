package com.aiinvestor.gateway.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================
 * 统一 API 响应体 - 所有接口返回的标准 JSON 格式
 * ============================================================
 *
 * 设计理念（大厂规范）：
 *   无论接口成功还是失败，返回的 JSON 结构必须一致。
 *   这样前端只需要写一套解析逻辑，不用每个接口都做 if-else。
 *
 * 响应示例（成功）：
 *   {"code": 0, "message": "ok", "data": {...}}
 *
 * 响应示例（失败）：
 *   {"code": 401, "message": "请先登录", "data": null}
 *
 * 泛型 <T> 的作用：
 *   允许不同接口的 data 字段有不同的类型（聊天数据、用户信息、列表等），
 *   同时保持外层 code/message 格式不变。
 *
 * @param <T> data 字段的具体类型
 * @author AI Investor Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    /** 业务状态码。0 = 成功，非 0 = 失败 */
    private int code;

    /** 状态描述信息 */
    private String message;

    /** 响应数据（泛型，可为 null） */
    private T data;

    /**
     * 快速构建成功响应。
     * 使用泛型方法，返回类型自动推断。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return code=0 的成功响应
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    /**
     * 快速构建失败响应。
     *
     * @param code    业务错误码
     * @param message 错误描述（会直接展示给用户）
     * @param <T>     数据类型（失败时 data 为 null）
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}

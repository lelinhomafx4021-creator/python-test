package com.example.security.controller;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result {
    private Integer code;
    private String msg;
    private Object data;

    public static Result ok(Object data) {
        return new Result(200, "success", data);
    }

    public static Result fail(Integer code, String msg) {
        return new Result(code, msg, null);
    }
}

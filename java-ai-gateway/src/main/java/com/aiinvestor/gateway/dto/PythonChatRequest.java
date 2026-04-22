package com.aiinvestor.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Java -> Python 能力层请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PythonChatRequest {
    private String message;

    @JsonProperty("thread_id")
    private String threadId;

    @JsonProperty("trace_id")
    private String traceId;
}

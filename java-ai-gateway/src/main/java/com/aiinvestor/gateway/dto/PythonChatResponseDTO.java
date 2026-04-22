package com.aiinvestor.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 数据传输对象 (DTO)：专门用来承接从 Python AI 引擎返回的 JSON 数据
 * 
 * 面试要点：
 * 1. 为什么用 DTO 而不是直接用 Map？—— 因为 DTO 提供类型检查，代码更健壮，易于维护。
 * 2. 字段映射：通过 @JsonProperty 解决 Java(驼峰) 与 Python(下划线) 的命名差异。
 */
@Data
public class PythonChatResponseDTO {
    // 状态码：200 表示成功，其他表示失败
    private int code;
    // 提示信息
    private String message;
    // 核心业务数据部分
    private DataResult data;

    @Data
    public static class DataResult {
        // AI 链路追踪唯一标识
        @JsonProperty("trace_id")
        private String traceId;
        
        // AI 给出的回答原文（支持 Markdown 格式）
        private String answer;
        
        // 回答依据的来源（比如：研报文件名、网页链接）
        private String source;
        
        // 意图识别结果（由 Python 的 IntentNode 识别得出）
        private String intent;
        
        // 评审是否通过：LangGraph 中的 Critic 节点会判断回答是否含有幻觉
        @JsonProperty("review_passed")
        private boolean reviewPassed;
        
        // 评审未通过的原因（如果有幻觉，这里会说明原因）
        @JsonProperty("review_reason")
        private String reviewReason;

        // A2A 消息详情：多智能体之间思考和对话的过程
        @JsonProperty("a2a_messages")
        private List<Map<String, Object>> a2aMessages;

        // --- 核心：AI 提取出来的结构化投研指标 (如结论、风险点) ---
        @JsonProperty("structured_data")
        private Map<String, Object> structuredData;
    }
}

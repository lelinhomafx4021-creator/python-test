package com.aiinvestor.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * ============================================================
 * Python AI 引擎返回的聊天响应 - 数据传输对象 (DTO)
 * ============================================================
 *
 * 架构说明：
 *   Python 的 AI 引擎（基于 LangGraph）分析完毕后，
 *   返回结构化的投研结果。本类就是这套结果在 Java 侧的"翻译"。
 *
 * 面试要点：
 *   为什么用 DTO 而不是直接 Map<String, Object>？
 *   1. 类型安全：编译期就能发现字段拼写错误，Map 要到运行时才报错
 *   2. 自文档化：字段名 + 注释就是 API 文档
 *   3. IDE 友好：自动补全、重构、查找引用
 *   4. 维护性：字段变更时编译器会告诉你哪里需要改
 *
 * @author AI Investor Team
 */
@Data
public class PythonChatResponseDTO {

    /** 状态码：200 表示成功，其他表示失败 */
    private int code;

    /** 状态描述信息 */
    private String message;

    /** 核心业务数据（嵌套对象，见下方 DataResult） */
    private DataResult data;

    /**
     * 响应数据的具体内容。
     * 使用静态内部类是为了保持命名空间的整洁。
     */
    @Data
    public static class DataResult {

        /** 全链路追踪 ID（与请求中的 trace_id 对应） */
        @JsonProperty("trace_id")
        private String traceId;

        /** AI 生成的最终回答（支持 Markdown 格式渲染） */
        private String answer;

        /** 回答的数据来源（如：研报文件名、网页链接） */
        private String source;

        /**
         * 意图识别结果。
         * Python 的 IntentNode（LangGraph 的一个节点）会先判断用户
         * 是想"投资分析"还是"闲聊"，从而走不同的处理分支。
         */
        private String intent;

        /**
         * 幻觉检测结果。
         * LangGraph 中的 Critic 节点（评审员）会检查 AI 回答是否
         * 包含虚假信息。这里存入 JSON 时用下划线风格。
         */
        @JsonProperty("review_passed")
        private boolean reviewPassed;

        /** 如果 review_passed=false，这里会说明检测到的具体问题 */
        @JsonProperty("review_reason")
        private String reviewReason;

        /**
         * A2A（Agent-to-Agent）消息记录。
         * 多智能体协作时，各个 Agent 之间的思考对话过程会记录在这里。
         * 每个 Map 代表一个 Agent 的一次发言。
         */
        @JsonProperty("a2a_messages")
        private List<Map<String, Object>> a2aMessages;

        /**
         * 【核心】AI 从非结构化文本中提取的结构化投研指标。
         *
         * 例如：
         * {
         *   "conclusion": "建议买入，目标价 150 元",
         *   "risk_points": ["行业竞争加剧", "政策不确定性"],
         *   "financial_metrics": {"pe": 25.3, "roe": 0.18}
         * }
         *
         * 这些结构化数据可供前端渲染成图表或卡片。
         */
        @JsonProperty("structured_data")
        private Map<String, Object> structuredData;
    }
}

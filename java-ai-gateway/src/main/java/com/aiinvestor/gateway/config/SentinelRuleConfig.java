package com.aiinvestor.gateway.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则初始化。
 * 一期先用最简单的本地规则，保护行情查询、模拟下单和 AI 流式问答入口。
 */
@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildRule("/api/v1/market/quotes", 20));
        rules.add(buildRule("/api/v1/paper/orders", 10));
        rules.add(buildRule("/gateway/ai/chat/stream", 8));
        FlowRuleManager.loadRules(rules);
    }

    private FlowRule buildRule(String resource, double count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }
}

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

    private final SentinelProperties sentinelProperties;

    public SentinelRuleConfig(SentinelProperties sentinelProperties) {
        this.sentinelProperties = sentinelProperties;
    }

    /**
     * 应用启动后初始化 Sentinel 限流规则。
     * 规则列表从 application.yml 的 app.sentinel.rules 配置读取。
     */
    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();
        for (SentinelProperties.Rule ruleConfig : sentinelProperties.getRules()) {
            rules.add(buildRule(ruleConfig.getResource(), ruleConfig.getQps()));
        }
        FlowRuleManager.loadRules(rules);
    }

    /**
     * 构建单条 Sentinel QPS 限流规则。
     *
     * @param resource 资源路径（接口路径）
     * @param count    QPS 阈值
     * @return 流量控制规则对象
     */
    private FlowRule buildRule(String resource, double count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }
}

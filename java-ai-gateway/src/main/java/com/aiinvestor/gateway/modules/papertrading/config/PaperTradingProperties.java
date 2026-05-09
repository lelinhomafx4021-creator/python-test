package com.aiinvestor.gateway.modules.papertrading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 模拟交易配置。
 *
 * 从 application.yml 中读取 app.paper-trading.* 配置项，
 * 避免在 PaperTradingService 中硬编码初始资金等业务参数。
 *
 * 配置示例：
 * app:
 *   paper-trading:
 *     initial-cash: 1000000
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.paper-trading")
public class PaperTradingProperties {

    /**
     * 新建模拟账户的初始资金，默认 100 万。
     */
    private BigDecimal initialCash = new BigDecimal("1000000");
}

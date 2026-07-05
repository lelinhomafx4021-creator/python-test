package com.aiinvestor.gateway.modules.shared.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized cache TTL settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {

    private long marketQuoteTtlSeconds = 30;

    private long emailCodeTtlSeconds = 300;

    private long emailCooldownTtlSeconds = 60;

    private long accountLockSeconds = 10;
}

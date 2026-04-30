package com.aiinvestor.gateway.modules.shared.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用缓存配置。
 * 统一管理行情缓存与账户锁的过期时间，避免把数字散落在业务代码里。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {

    /** 行情缓存秒数。 */
    private long marketQuoteTtlSeconds = 30;

    /** 账户分布式锁秒数。 */
    private long accountLockSeconds = 10;
}

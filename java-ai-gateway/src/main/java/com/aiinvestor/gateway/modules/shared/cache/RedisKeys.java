package com.aiinvestor.gateway.modules.shared.cache;

/**
 * Centralized Redis key naming rules.
 *
 * <p>Format: {domain}:{entity}:{scope}:{id}
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    public static String marketQuote(String symbol) {
        return "market:quote:" + safe(symbol);
    }

    public static String emailCode(String scene, String email) {
        return "email:code:" + safe(scene) + ":" + safe(email);
    }

    public static String emailCooldown(String scene, String email) {
        return "email:cooldown:" + safe(scene) + ":" + safe(email);
    }

    public static String paperAccountLock(Long accountId) {
        return "paper:account:lock:" + accountId;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}

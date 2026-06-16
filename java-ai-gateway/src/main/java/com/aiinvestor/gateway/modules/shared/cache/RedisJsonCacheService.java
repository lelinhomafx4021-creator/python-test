package com.aiinvestor.gateway.modules.shared.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Redis JSON 缓存服务。
 * <p>
 * 基于 StringRedisTemplate 存储 JSON 字符串，提供统一的序列化/反序列化封装，
 * 减少各业务模块重复编写 Jackson 序列化代码。
 * <p>
 * 设计要点：
 * - 使用 StringRedisTemplate（而非 RedisTemplate&lt;Object, Object&gt;）避免二进制序列化，
 *   使缓存内容可在 Redis CLI 中直接查看，便于调试。
 * - 读写异常静默降级（log.warn 后返回 null / 不抛异常），避免缓存故障拖垮主业务。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RedisJsonCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 读取缓存并反序列化为目标类型。
     *
     * @param key   缓存键
     * @param clazz 目标类型 class
     * @param <T>   泛型类型
     * @return 反序列化后的对象，key 不存在或反序列化失败时返回 null
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败 key={}", key, e);
            return null;
        }
    }

    /**
     * 将对象序列化后写入缓存。
     *
     * @param key   缓存键
     * @param value 待缓存的对象（会被 Jackson 序列化为 JSON）
     * @param ttl   过期时间（Duration 类型，如 Duration.ofSeconds(30)）
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败 key={}", key, e);
        }
    }

    /**
     * 删除指定缓存。
     *
     * @param key 缓存键
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 批量写入缓存（遍历调用 set）。
     *
     * @param entries 键值对集合
     * @param ttl     统一的过期时间
     */
    public void setAll(Map<String, Object> entries, Duration ttl) {
        for (var entry : entries.entrySet()) {
            set(entry.getKey(), entry.getValue(), ttl);
        }
    }
}

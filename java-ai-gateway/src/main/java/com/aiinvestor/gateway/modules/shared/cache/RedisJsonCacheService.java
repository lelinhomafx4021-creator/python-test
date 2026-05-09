package com.aiinvestor.gateway.modules.shared.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis JSON 缓存服务。
 * 用字符串模板存储 JSON，减少为每个业务域重复写序列化逻辑。
 */
@Service
public class RedisJsonCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisJsonCacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisJsonCacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取缓存并反序列化。
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
     * 写入缓存。
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
     * 删除缓存。
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 批量写入缓存。
     */
    public void setAll(Map<String, Object> entries, Duration ttl) {
        for (var entry : entries.entrySet()) {
            set(entry.getKey(), entry.getValue(), ttl);
        }
    }
}

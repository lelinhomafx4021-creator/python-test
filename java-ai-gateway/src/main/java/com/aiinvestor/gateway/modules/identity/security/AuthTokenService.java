package com.aiinvestor.gateway.modules.identity.security;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthTokenService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthTokenProperties authTokenProperties;

    public AuthTokenService(StringRedisTemplate stringRedisTemplate, AuthTokenProperties authTokenProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authTokenProperties = authTokenProperties;
    }

    public String issueToken(UserDO user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                buildTokenKey(token),
                String.valueOf(user.getId()),
                Duration.ofSeconds(authTokenProperties.getTokenTtlSeconds())
        );
        return token;
    }

    public Long resolveUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String userId = stringRedisTemplate.opsForValue().get(buildTokenKey(token));
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return Long.valueOf(userId);
    }

    public void revokeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        stringRedisTemplate.delete(buildTokenKey(token));
    }

    private String buildTokenKey(String token) {
        return authTokenProperties.getRedisKeyPrefix() + token;
    }
}

package com.schoolai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private static final String KEY_PREFIX = "school-ai:login-session:";
    private final StringRedisTemplate redisTemplate;

    public void replaceSession(Long userId, String sessionId, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), sessionId, ttl);
    }

    public boolean isCurrentSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        String currentSessionId = redisTemplate.opsForValue().get(key(userId));
        return sessionId.equals(currentSessionId);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}

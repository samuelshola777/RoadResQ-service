package com.roadresq.roadresq.utils.appsecurity;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class AccountAccessCache {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAccessCache.class);

    private static final RedisScript<String> LOOKUP = RedisScript.of(
            new ClassPathResource("redis/account-access-lookup.lua"), String.class);

    private static final RedisScript<Long> FILL = RedisScript.of(
            new ClassPathResource("redis/account-access-fill.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public AccountAccessCache(
            StringRedisTemplate redis,
            @Value("${app.account-access-cache.enabled:false}") boolean enabled
    ) {
        this.redis = redis;
        this.enabled = enabled;
    }

    String lookup(Claims claims, String token) {
        if (!enabled) {
            return "";
        }

        try {
            String result = redis.execute(LOOKUP, keys(claims, token), UUID.randomUUID().toString());
            return result == null ? "" : result;
        } catch (RuntimeException exception) {
            LOG.debug("Redis access lookup unavailable", exception);
            return "";
        }
    }

    void fill(Claims claims, String token, String generation) {
        if (!enabled || generation.isBlank() || "hit".equals(generation)
                || claims.getExpiration() == null
                || claims.getExpiration().getTime() <= System.currentTimeMillis()) {
            return;
        }

        try {
            redis.execute(FILL, keys(claims, token), generation,
                    Long.toString(claims.getExpiration().getTime()));
        } catch (RuntimeException exception) {
            LOG.debug("Redis access cache update unavailable", exception);
        }
    }

    private static List<String> keys(Claims claims, String token) {
        String prefix = prefix(claims.get("userId", String.class));
        return List.of(prefix + "blocked", prefix + "generation", prefix + "token:" + digest(token));
    }

    private static String prefix(String userId) {
        return "roadresq:access:v1:{" + digest(userId) + "}:";
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
package com.roadresq.roadresq.rescueRequest.dispatch;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorPaymentHold {
    private static final String PREFIX = "roadresq:{dispatch}:";
    private static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[1])
            local job = redis.call('GET', KEYS[2])
            if owner == ARGV[1] and job == ARGV[2] then
                return redis.call('PEXPIRETIME', KEYS[1])
            end
            if owner or job then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'PXAT', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'PXAT', ARGV[3])
            return tonumber(ARGV[3])
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('GET', KEYS[2]) == ARGV[2] then
                return redis.call('DEL', KEYS[1], KEYS[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public Instant claim(String requestId, String vendorId, Instant deadline) {
        Long expires = redis.execute(CLAIM, keys(requestId, vendorId), vendorId, requestId,
                Long.toString(deadline.toEpochMilli()));
        return expires == null || expires <= 0 ? null : Instant.ofEpochMilli(expires);
    }

    public String vendorFor(String requestId) {
        return redis.opsForValue().get(PREFIX + "request:" + requestId);
    }

    public String requestFor(String vendorId) {
        return redis.opsForValue().get(PREFIX + "vendor:" + vendorId);
    }

    public void release(String requestId) {
        String vendorId = vendorFor(requestId);
        if (vendorId != null) {
            redis.execute(RELEASE, keys(requestId, vendorId), vendorId, requestId);
        }
    }

    private List<String> keys(String requestId, String vendorId) {
        return List.of(PREFIX + "request:" + requestId, PREFIX + "vendor:" + vendorId);
    }
}
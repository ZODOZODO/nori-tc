package com.nori.tc.db.starter.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class TcRedisTemplateCrudRepository implements TcRedisCrudRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOperations;

    public TcRedisTemplateCrudRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOperations = redisTemplate.opsForValue();
    }

    @Override
    public void set(String key, Object value) {
        valueOperations.set(key, value);
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        valueOperations.set(key, value, ttl);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        Boolean result = valueOperations.setIfAbsent(key, value, ttl);
        return result != null && result;
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(valueOperations.get(key));
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = valueOperations.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return result != null && result;
    }

    @Override
    public long delete(Collection<String> keys) {
        Long deleted = redisTemplate.delete(keys);
        return deleted == null ? 0L : deleted;
    }

    @Override
    public boolean exists(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return result != null && result;
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        Boolean result = redisTemplate.expire(key, ttl);
        return result != null && result;
    }

    @Override
    public Long increment(String key, long delta) {
        return valueOperations.increment(key, delta);
    }

    @Override
    public Map<String, Object> multiGet(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keyList = new ArrayList<>(keys);
        List<Object> values = valueOperations.multiGet(keyList);
        if (values == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyList.size(); i++) {
            result.put(keyList.get(i), values.get(i));
        }
        return result;
    }

    @Override
    public void multiSet(Map<String, Object> values, Duration ttl) {
        if (values.isEmpty()) {
            return;
        }
        valueOperations.multiSet(values);
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            values.keySet().forEach(key -> redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS));
        }
    }
}

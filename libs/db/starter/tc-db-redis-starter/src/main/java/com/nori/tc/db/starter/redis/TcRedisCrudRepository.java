package com.nori.tc.db.starter.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface TcRedisCrudRepository {

    void set(String key, Object value);

    void set(String key, Object value, Duration ttl);

    boolean setIfAbsent(String key, Object value, Duration ttl);

    Optional<Object> get(String key);

    <T> Optional<T> get(String key, Class<T> type);

    boolean delete(String key);

    long delete(Collection<String> keys);

    boolean exists(String key);

    boolean expire(String key, Duration ttl);

    Long increment(String key, long delta);

    Map<String, Object> multiGet(Collection<String> keys);

    void multiSet(Map<String, Object> values, Duration ttl);
}

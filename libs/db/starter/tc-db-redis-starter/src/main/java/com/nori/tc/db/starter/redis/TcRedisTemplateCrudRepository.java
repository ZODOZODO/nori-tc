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

    
    /**
     * DB 스타터 구성 구성 요소를 초기화합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param redisTemplate DB 스타터 구성 처리에 사용하는 입력 값
     */
    public TcRedisTemplateCrudRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOperations = redisTemplate.opsForValue();
    }

    
    /**
     * DB 스타터 구성 설정 값을 반영합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value DB 스타터 구성 처리에 사용하는 입력 값
     */
    @Override
    public void set(String key, Object value) {
        valueOperations.set(key, value);
    }

    
    /**
     * DB 스타터 구성 설정 값을 반영합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value DB 스타터 구성 처리에 사용하는 입력 값
     * @param ttl 시간 관련 설정 값
     */
    @Override
    public void set(String key, Object value, Duration ttl) {
        valueOperations.set(key, value, ttl);
    }

    
    /**
     * DB 스타터 구성 설정 값을 반영합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value DB 스타터 구성 처리에 사용하는 입력 값
     * @param ttl 시간 관련 설정 값
     * @return 처리 성공 여부
     */
    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        Boolean result = valueOperations.setIfAbsent(key, value, ttl);
        return result != null && result;
    }

    
    /**
     * DB 스타터 구성의 현재 값을 조회합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB 스타터 구성 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @return 처리 성공 여부
     */
    @Override
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return result != null && result;
    }

    
    /**
     * DB 스타터 구성 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param keys DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
    @Override
    public long delete(Collection<String> keys) {
        Long deleted = redisTemplate.delete(keys);
        return deleted == null ? 0L : deleted;
    }

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @return 처리 성공 여부
     */
    @Override
    public boolean exists(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return result != null && result;
    }

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param ttl 시간 관련 설정 값
     * @return 처리 성공 여부
     */
    @Override
    public boolean expire(String key, Duration ttl) {
        Boolean result = redisTemplate.expire(key, ttl);
        return result != null && result;
    }

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param delta DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
    @Override
    public Long increment(String key, long delta) {
        return valueOperations.increment(key, delta);
    }

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param keys DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
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

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param values DB 스타터 구성 처리에 사용하는 입력 값
     * @param ttl 시간 관련 설정 값
     */
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

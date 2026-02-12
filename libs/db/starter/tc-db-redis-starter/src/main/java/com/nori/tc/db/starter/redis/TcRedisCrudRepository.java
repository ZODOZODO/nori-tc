package com.nori.tc.db.starter.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface TcRedisCrudRepository {

    
    /**
     * DB 스타터 구성 설정 값을 반영합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value DB 스타터 구성 처리에 사용하는 입력 값
     */
    void set(String key, Object value);

    
    /**
     * DB 스타터 구성 설정 값을 반영합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value DB 스타터 구성 처리에 사용하는 입력 값
     * @param ttl 시간 관련 설정 값
     */
    void set(String key, Object value, Duration ttl);

    
    /**
     * DB 스타터 구성 설정 값을 반영합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param value DB 스타터 구성 처리에 사용하는 입력 값
     * @param ttl 시간 관련 설정 값
     * @return 처리 성공 여부
     */
    boolean setIfAbsent(String key, Object value, Duration ttl);

    
    /**
     * DB 스타터 구성의 현재 값을 조회합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<Object> get(String key);

    <T> Optional<T> get(String key, Class<T> type);

    
    /**
     * DB 스타터 구성 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @return 처리 성공 여부
     */
    boolean delete(String key);

    
    /**
     * DB 스타터 구성 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param keys DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
    long delete(Collection<String> keys);

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @return 처리 성공 여부
     */
    boolean exists(String key);

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param ttl 시간 관련 설정 값
     * @return 처리 성공 여부
     */
    boolean expire(String key, Duration ttl);

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param key 대상 키 값
     * @param delta DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
    Long increment(String key, long delta);

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param keys DB 스타터 구성 처리에 사용하는 입력 값
     * @return DB 스타터 구성 처리 결과
     */
    Map<String, Object> multiGet(Collection<String> keys);

    
    /**
     * DB 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>데이터소스 및 저장소 빈 자동 구성 조건을 기준으로 처리합니다.</p>
     * @param values DB 스타터 구성 처리에 사용하는 입력 값
     * @param ttl 시간 관련 설정 값
     */
    void multiSet(Map<String, Object> values, Duration ttl);
}

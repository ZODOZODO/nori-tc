package com.nori.tc.comm.adapters.redis.runtime;

import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis-based equipment runtime CRUD service.
 *
 * This service is app-specific and encapsulates key naming/TTL rules.
 */
@Service
public class GatewayEquipmentRuntimeService {

    private static final String RUNTIME_KEY_PREFIX = "tc:comm:gateway:runtime:";
    private static final Logger log = LoggerFactory.getLogger(GatewayEquipmentRuntimeService.class);

    private final TcRedisCrudRepository repository;

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param repository 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     */
    public GatewayEquipmentRuntimeService(final TcRedisCrudRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
    }

    
    /**
     * 게이트웨이 Redis 어댑터 데이터의 저장/갱신을 처리합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param runtime 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public RedisEquipmentRuntime save(final RedisEquipmentRuntime runtime) {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        Objects.requireNonNull(runtime, "runtime is null");

        final String key = RUNTIME_KEY_PREFIX + runtime.getEquipmentId();
        final Long ttlSeconds = runtime.getTtlSeconds();

        if (ttlSeconds != null && ttlSeconds > 0) {
            repository.set(key, runtime, Duration.ofSeconds(ttlSeconds));
        } else {
            repository.set(key, runtime);
        }
        if (log.isDebugEnabled()) {
            log.debug("Runtime saved. eqpId={}, ttlSeconds={}", runtime.getEquipmentId(), ttlSeconds);
        }
        return runtime;
    }

    
    /**
     * 게이트웨이 Redis 어댑터에서 필요한 데이터를 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param equipmentId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    public Optional<RedisEquipmentRuntime> findById(final String equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        if (log.isDebugEnabled()) {
            log.debug("Runtime lookup. eqpId={}", equipmentId);
        }
        return repository.get(RUNTIME_KEY_PREFIX + equipmentId, RedisEquipmentRuntime.class);
    }

    
    /**
     * 게이트웨이 Redis 어댑터 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param equipmentId 설비 식별 정보
     */
    public void delete(final String equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        repository.delete(RUNTIME_KEY_PREFIX + equipmentId);
        if (log.isDebugEnabled()) {
            log.debug("Runtime deleted. eqpId={}", equipmentId);
        }
    }
}

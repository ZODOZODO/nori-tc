package com.nori.tc.apps.commgateway.redis;

import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis 기반 설비 런타임 CRUD 서비스
 */
@Service
public class GatewayEquipmentRuntimeService {

    private static final String RUNTIME_KEY_PREFIX = "tc:comm:gateway:runtime:";

    private final TcRedisCrudRepository repository;

    public GatewayEquipmentRuntimeService(final TcRedisCrudRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
    }

    public RedisEquipmentRuntime save(final RedisEquipmentRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime is null");

        final String key = RUNTIME_KEY_PREFIX + runtime.getEquipmentId();
        final Long ttlSeconds = runtime.getTtlSeconds();

        if (ttlSeconds != null && ttlSeconds > 0) {
            repository.set(key, runtime, Duration.ofSeconds(ttlSeconds));
        } else {
            repository.set(key, runtime);
        }

        return runtime;
    }

    public Optional<RedisEquipmentRuntime> findById(final String equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        return repository.get(RUNTIME_KEY_PREFIX + equipmentId, RedisEquipmentRuntime.class);
    }

    public void delete(final String equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        repository.delete(RUNTIME_KEY_PREFIX + equipmentId);
    }
}

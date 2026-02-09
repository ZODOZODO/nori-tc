package com.nori.tc.apps.commgateway.redis;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Redis 기반 설비 런타임 CRUD 서비스
 */
@Service
public class GatewayEquipmentRuntimeService {

    private final RedisEquipmentRuntimeRepository repository;

    public GatewayEquipmentRuntimeService(final RedisEquipmentRuntimeRepository repository) {
        this.repository = repository;
    }

    public RedisEquipmentRuntime save(final RedisEquipmentRuntime runtime) {
        return repository.save(runtime);
    }

    public Optional<RedisEquipmentRuntime> findById(final String equipmentId) {
        return repository.findById(equipmentId);
    }

    public void delete(final String equipmentId) {
        repository.deleteById(equipmentId);
    }
}

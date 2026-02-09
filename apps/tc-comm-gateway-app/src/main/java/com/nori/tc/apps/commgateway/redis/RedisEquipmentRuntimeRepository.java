package com.nori.tc.apps.commgateway.redis;

import org.springframework.data.repository.CrudRepository;

/**
 * Redis 설비 런타임 CRUD
 */
public interface RedisEquipmentRuntimeRepository extends CrudRepository<RedisEquipmentRuntime, String> {
}

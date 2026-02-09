package com.nori.tc.apps.commgateway.redis;

import org.springframework.data.repository.CrudRepository;

/**
 * Redis Quarantine CRUD
 */
public interface RedisQuarantineRepository extends CrudRepository<RedisQuarantineEntry, String> {
}

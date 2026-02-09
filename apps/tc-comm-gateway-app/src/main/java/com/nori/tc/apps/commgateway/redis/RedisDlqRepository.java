package com.nori.tc.apps.commgateway.redis;

import org.springframework.data.repository.CrudRepository;

/**
 * Redis DLQ CRUD
 */
public interface RedisDlqRepository extends CrudRepository<RedisDlqEntry, String> {
}

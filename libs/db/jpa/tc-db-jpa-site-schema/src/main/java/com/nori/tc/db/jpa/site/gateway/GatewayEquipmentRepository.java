package com.nori.tc.db.jpa.site.gateway;

import org.springframework.data.repository.CrudRepository;

/**
 * Gateway 설비 마스터 CRUD
 */
public interface GatewayEquipmentRepository extends CrudRepository<GatewayEquipmentEntity, String> {
}

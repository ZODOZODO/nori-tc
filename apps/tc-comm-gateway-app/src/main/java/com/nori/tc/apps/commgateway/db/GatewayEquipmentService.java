package com.nori.tc.apps.commgateway.db;

import com.nori.tc.db.jpa.site.gateway.GatewayEquipmentEntity;
import com.nori.tc.db.jpa.site.gateway.GatewayEquipmentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gateway 설비 마스터 조회 서비스
 */
@Service
public class GatewayEquipmentService {

    private final GatewayEquipmentRepository repository;

    public GatewayEquipmentService(final GatewayEquipmentRepository repository) {
        this.repository = repository;
    }

    public List<GatewayEquipmentEntity> findAll() {
        final List<GatewayEquipmentEntity> results = new ArrayList<>();
        repository.findAll().forEach(results::add);
        return results;
    }

    public Optional<GatewayEquipmentEntity> findById(final String equipmentId) {
        return repository.findById(equipmentId);
    }
}

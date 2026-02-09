package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.apps.commgateway.db.GatewayEquipmentEntity;
import com.nori.tc.apps.commgateway.db.GatewayEquipmentService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 애플리케이션 시작 시 설비 컨텍스트를 선로딩합니다.
 *
 * - 낮은 지연을 위해, 최초 메시지 수신 전에 컨텍스트를 준비합니다.
 * - 운영에서는 설비 수가 매우 많을 수 있으므로, 필요 시 lazy 로딩으로 전환 가능합니다.
 */
@Component
public class GatewayBootstrapRunner implements ApplicationRunner {

    private final GatewayEquipmentService equipmentService;
    private final GatewayProcessingService processingService;

    public GatewayBootstrapRunner(
            final GatewayEquipmentService equipmentService,
            final GatewayProcessingService processingService
    ) {
        this.equipmentService = Objects.requireNonNull(equipmentService, "equipmentService is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
    }

    @Override
    public void run(final ApplicationArguments args) {
        final List<GatewayEquipmentEntity> equipmentList = equipmentService.findAll();
        equipmentList.stream()
                .filter(GatewayEquipmentEntity::isEnabled)
                .forEach(processingService::register);
    }
}

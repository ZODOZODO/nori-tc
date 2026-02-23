package com.nori.tc.comm.gateway.context.service;

import com.nori.tc.comm.gateway.context.model.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.model.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.model.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.context.port.EquipmentContextProfileProvider;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Gateway 부팅 시 EquipmentContextRegistry를 초기 적재하는 부트스트랩입니다.
 *
 * <p>3차 구조 분해 후 {@code context.service} 계층으로 이동했으며,
 * 조회 포트({@code context.port})와 모델({@code context.model})을 조합해 초기 상태를 구성합니다.</p>
 * <p>정책:</p>
 * <p>- enabled=true: desiredState를 STARTED로 적재</p>
 * <p>- enabled=false: desiredState를 ENDED로 적재</p>
 * <p>- 실제 연결 시도는 Netty bootstrap 정책(기존 로직)에 위임</p>
 */
@Component
public class EquipmentContextBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EquipmentContextBootstrap.class);

    private final EquipmentContextProfileProvider profileProvider;
    private final EquipmentContextRegistry contextRegistry;

    /**
     * 부트스트랩 생성자입니다.
     */
    public EquipmentContextBootstrap(
            final EquipmentContextProfileProvider profileProvider,
            final EquipmentContextRegistry contextRegistry
    ) {
        this.profileProvider = Objects.requireNonNull(profileProvider, "profileProvider is null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
    }

    /**
     * 애플리케이션 초기화 시점에 전체 설비 프로파일을 메모리 컨텍스트로 적재합니다.
     */
    @PostConstruct
    public void loadInitialContexts() {
        final List<EquipmentContextProfile> profiles = profileProvider.findAllProfiles();
        int startedDesiredCount = 0;
        int endedDesiredCount = 0;

        for (EquipmentContextProfile profile : profiles) {
            final String eqpId = profile.equipmentInfo().equipmentId();
            final boolean enabled = profile.equipmentInfo().enabled();
            final EquipmentDesiredState desiredState = enabled
                    ? EquipmentDesiredState.STARTED
                    : EquipmentDesiredState.ENDED;
            final EquipmentRuntimeState runtimeState = enabled
                    ? EquipmentRuntimeState.DISCONNECTED
                    : EquipmentRuntimeState.REGISTERED;

            /**
             * 부팅 적재 단계에서도 설비 단위 로그를 명확히 분리하기 위해
             * upsert 수행 구간 전체를 eqpId MDC 스코프로 감쌉니다.
             *
             * 이렇게 하면 contextRegistry 내부에서 남기는 "created/refreshed" 로그까지
             * 동일한 eqpId로 라우팅되어 EQP 파일에 안정적으로 기록됩니다.
             */
            try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "부팅 컨텍스트 적재 시작. eqpId={}, enabled={}, desiredState={}, runtimeState={}",
                            eqpId,
                            enabled,
                            desiredState,
                            runtimeState
                    );
                }

                contextRegistry.upsertProfile(
                        profile,
                        desiredState,
                        runtimeState,
                        "BOOTSTRAP_LOAD",
                        "BOOTSTRAP"
                );

                if (log.isDebugEnabled()) {
                    log.debug("부팅 컨텍스트 적재 완료. eqpId={}", eqpId);
                }
            }

            if (enabled) {
                startedDesiredCount++;
            } else {
                endedDesiredCount++;
            }
        }

        log.info("Equipment context bootstrap completed. total={}, desiredStarted={}, desiredEnded={}",
                profiles.size(),
                startedDesiredCount,
                endedDesiredCount);
        if (log.isDebugEnabled()) {
            log.debug("Equipment context registry size after bootstrap={}", contextRegistry.size());
        }
    }
}

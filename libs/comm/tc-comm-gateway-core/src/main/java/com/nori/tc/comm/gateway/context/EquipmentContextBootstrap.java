package com.nori.tc.comm.gateway.context;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Gateway 부팅 시 EquipmentContextRegistry를 초기 적재하는 부트스트랩입니다.
 *
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
            final boolean enabled = profile.equipmentInfo().enabled();
            final EquipmentDesiredState desiredState = enabled
                    ? EquipmentDesiredState.STARTED
                    : EquipmentDesiredState.ENDED;
            final EquipmentRuntimeState runtimeState = enabled
                    ? EquipmentRuntimeState.DISCONNECTED
                    : EquipmentRuntimeState.REGISTERED;

            contextRegistry.upsertProfile(
                    profile,
                    desiredState,
                    runtimeState,
                    "BOOTSTRAP_LOAD",
                    "BOOTSTRAP"
            );

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


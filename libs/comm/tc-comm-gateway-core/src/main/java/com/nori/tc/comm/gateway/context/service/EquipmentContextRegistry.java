package com.nori.tc.comm.gateway.context.service;

import com.nori.tc.comm.gateway.context.model.EquipmentContext;
import com.nori.tc.comm.gateway.context.model.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.model.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.model.EquipmentRuntimeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설비 컨텍스트 인메모리 저장소입니다.
 *
 * <p>3차 패키지 분해 후 이 클래스는 {@code context.service} 계층에 위치하며,
 * 상태 저장/조회/갱신 조정 책임을 담당합니다.</p>
 * <p>eqpId를 키로 EquipmentContext를 관리합니다.</p>
 * <p>Gateway 인스턴스 로컬 상태이므로 멀티 인스턴스 공유는 별도 저장소(예: Redis)로 확장합니다.</p>
 */
@Component
public class EquipmentContextRegistry {

    private static final Logger log = LoggerFactory.getLogger(EquipmentContextRegistry.class);

    private final Map<String, EquipmentContext> contexts = new ConcurrentHashMap<>();

    /**
     * 프로파일을 신규 등록하거나 기존 컨텍스트를 갱신합니다.
     *
     * <p>기존 컨텍스트가 있으면 desired/runtime 상태는 유지하고 프로파일만 교체합니다.</p>
     */
    public EquipmentContext upsertProfile(
            final EquipmentContextProfile profile,
            final EquipmentDesiredState defaultDesiredState,
            final EquipmentRuntimeState defaultRuntimeState,
            final String eventType,
            final String traceId
    ) {
        Objects.requireNonNull(profile, "profile is null");
        Objects.requireNonNull(defaultDesiredState, "defaultDesiredState is null");
        Objects.requireNonNull(defaultRuntimeState, "defaultRuntimeState is null");

        final String eqpId = profile.equipmentInfo().equipmentId();
        return contexts.compute(eqpId, (key, current) -> {
            if (current == null) {
                final EquipmentContext created = new EquipmentContext(
                        profile,
                        defaultDesiredState,
                        defaultRuntimeState,
                        eventType,
                        traceId
                );
                log.info("Equipment context created. eqpId={}, desiredState={}, runtimeState={}, eventType={}",
                        eqpId, defaultDesiredState, defaultRuntimeState, eventType);
                return created;
            }

            current.refreshProfile(profile, eventType, traceId);
            if (log.isDebugEnabled()) {
                log.debug("Equipment context profile refreshed. eqpId={}, eventType={}, traceId={}",
                        eqpId, eventType, traceId);
            }
            return current;
        });
    }

    /**
     * eqpId로 컨텍스트를 조회합니다.
     */
    public Optional<EquipmentContext> find(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(contexts.get(eqpId.trim()));
    }

    /**
     * eqpId로 컨텍스트를 제거합니다.
     */
    public boolean remove(final String eqpId, final String eventType, final String traceId) {
        if (eqpId == null || eqpId.isBlank()) {
            return false;
        }
        final EquipmentContext removed = contexts.remove(eqpId.trim());
        if (removed == null) {
            return false;
        }
        removed.updateDesiredState(EquipmentDesiredState.DELETED, eventType, traceId);
        removed.updateRuntimeState(EquipmentRuntimeState.DELETED, eventType, traceId);
        log.info("Equipment context removed. eqpId={}, eventType={}, traceId={}", eqpId, eventType, traceId);
        return true;
    }

    /**
     * 컨텍스트 개수를 반환합니다.
     */
    public int size() {
        return contexts.size();
    }

    /**
     * 현재 컨텍스트 스냅샷 컬렉션을 반환합니다.
     *
     * <p>호출부에서 읽기 전용으로만 사용해야 합니다.</p>
     */
    public Collection<EquipmentContext> snapshot() {
        return ListCopy.of(contexts.values());
    }

    /**
     * JDK 21 환경에서 간단한 불변 컬렉션 래핑 유틸입니다.
     */
    private static final class ListCopy {
        /**
         * ListCopy 생성자를 초기화합니다.
         *
         */

        private ListCopy() {
        }

        static <T> Collection<T> of(final Collection<T> source) {
            return source == null ? java.util.List.of() : java.util.List.copyOf(source);
        }
    }
}

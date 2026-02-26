package com.nori.tc.business.adapters.db.eqp;

import com.nori.tc.business.core.messaging.BusinessEqpRoutePartitionLookupPort;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.eqp.TcEqp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link BusinessEqpRoutePartitionLookupPort}의 DB 기반 구현 어댑터입니다.
 *
 * <p>역할:</p>
 * <p>- {@link TcEqpStore}를 통해 {@code tc_eqp}를 조회하고,
 *   Gateway 고정 라우팅에 필요한 {@code route_partition} 값을 반환합니다.</p>
 *
 * <p>운영 관점 주의사항:</p>
 * <p>- 설비는 존재하지만 {@code route_partition}이 비어 있는 경우, 아직 라우팅 배정이 완료되지 않은 상태일 수 있습니다.</p>
 * <p>- 본 어댑터는 정책 판단(예외 변환/재시도)을 하지 않고 조회 결과만 전달하며,
 *   발행 허용 여부 판단은 상위 호출자(예: Kafka publisher)에서 수행합니다.</p>
 */
@Component
public class BusinessEqpRoutePartitionDbAdapter implements BusinessEqpRoutePartitionLookupPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessEqpRoutePartitionDbAdapter.class);

    private final TcEqpStore eqpStore;

    /**
     * DB 조회 어댑터 의존성을 초기화합니다.
     *
     * @param eqpStore tc_eqp 저장소 포트
     */
    public BusinessEqpRoutePartitionDbAdapter(final TcEqpStore eqpStore) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        log.info("BusinessEqpRoutePartitionDbAdapter initialized. source=tc_eqp.route_partition");
    }

    /**
     * 설비 ID(eqpId)로 고정 라우팅 partition을 조회합니다.
     *
     * <p>동작 순서:</p>
     * <p>1) eqpId 입력값 기본 검증</p>
     * <p>2) {@code tc_eqp} 단건 조회</p>
     * <p>3) 조회 결과에서 {@code route_partition} 추출</p>
     *
     * @param eqpId 설비 식별자(비즈니스 키)
     * @return route_partition 조회 결과(Optional)
     */
    @Override
    public Optional<Integer> findRoutePartitionByEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            if (log.isDebugEnabled()) {
                log.debug("route_partition 조회를 건너뜁니다. 사유=eqpId 비어 있음");
            }
            return Optional.empty();
        }

        final Optional<TcEqp> found = eqpStore.findByEqpId(eqpId);
        if (found.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("route_partition 조회 결과 없음. eqpId={}, 사유=tc_eqp 미존재", eqpId);
            }
            return Optional.empty();
        }

        final Integer routePartition = found.get().routePartition();
        if (log.isDebugEnabled()) {
            log.debug("route_partition 조회 완료. eqpId={}, routePartition={}, enabled={}",
                    eqpId,
                    routePartition,
                    found.get().enabled());
        }
        return Optional.ofNullable(routePartition);
    }
}

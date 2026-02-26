package com.nori.tc.business.core.messaging;

import java.util.Optional;

/**
 * Business에서 Gateway 대상 EQP 명령 발행 시 사용할 고정 라우팅 partition 조회 포트입니다.
 *
 * <p>역할:</p>
 * <p>- {@code tc_eqp.route_partition}를 조회하여 Business Kafka 발행 어댑터가
 *   {@code ProducerRecord(topic, partition, key, value)} 형태로 명시 partition 발행을 수행할 수 있게 합니다.</p>
 * <p>- Core/Kafka 어댑터가 DB 저장소 구현에 직접 의존하지 않도록 조회 책임을 포트로 분리합니다.</p>
 *
 * <p>설계 주의사항:</p>
 * <p>- 반환 값이 비어 있거나(null) route_partition이 미배정인 경우는 운영 설정 오류로 간주합니다.</p>
 * <p>- U12 단계에서는 조회 결과를 캐시하지 않고 매 발행 시점 기준으로 조회합니다.
 *   (추후 성능 이슈가 확인되면 캐시 계층을 별도 어댑터로 추가)</p>
 */
@FunctionalInterface
public interface BusinessEqpRoutePartitionLookupPort {

    /**
     * 설비 ID(eqpId)로 고정 라우팅 partition을 조회합니다.
     *
     * <p>호출 시점:</p>
     * <p>- Business -> Gateway EQP command 발행 직전</p>
     *
     * <p>반환 규칙:</p>
     * <p>- 설비가 존재하고 route_partition이 배정되어 있으면 해당 partition 반환</p>
     * <p>- 설비가 없거나 route_partition이 비어 있으면 {@link Optional#empty()}</p>
     *
     * @param eqpId 설비 식별자(비즈니스 키)
     * @return route_partition 조회 결과(Optional)
     */
    Optional<Integer> findRoutePartitionByEqpId(String eqpId);

    /**
     * 기본 no-op 구현을 반환합니다.
     *
     * <p>테스트/초기 부트스트랩 환경에서 조회 어댑터가 아직 준비되지 않은 경우 사용합니다.</p>
     *
     * @return 항상 빈 값을 반환하는 no-op 조회 포트
     */
    static BusinessEqpRoutePartitionLookupPort noop() {
        return ignored -> Optional.empty();
    }
}

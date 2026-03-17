package com.nori.tc.business.core.work;

import com.nori.tc.business.domain.work.WorkEntry;

import java.util.List;
import java.util.Optional;

/**
 * Work 인메모리 캐시 조회 포트입니다.
 *
 * <p>
 * hot path에서 DB 조회 대신 메모리 캐시를 통해 work 컨텍스트를 빠르게 조회합니다.
 * 캐시는 eqpId 단위로 lazy 로드되며, work 변경 시 {@link BusinessWorkMutationPort}를 통해 동기화됩니다.
 * </p>
 *
 * <p>반환되는 {@link WorkEntry}는 work, lot, carrier, controlJob, processJob을 포함한 전체 컨텍스트입니다.</p>
 */
public interface BusinessWorkProvider {

    /**
     * eqpId 기준으로 첫 번째 WorkEntry를 반환합니다.
     *
     * <p>설비에 work가 한 건인 경우 단건 조회 편의 메서드로 사용합니다.</p>
     *
     * @param eqpId 설비 ID
     * @return 첫 번째 WorkEntry (없으면 empty)
     */
    Optional<WorkEntry> findByEqpId(String eqpId);

    /**
     * eqpId 기준으로 모든 WorkEntry를 반환합니다.
     *
     * @param eqpId 설비 ID
     * @return WorkEntry 목록 (없으면 빈 리스트)
     */
    List<WorkEntry> findAllByEqpId(String eqpId);

    /**
     * eqpId + lotId 기준으로 첫 번째 WorkEntry를 반환합니다.
     *
     * @param eqpId 설비 ID
     * @param lotId lot ID
     * @return 매칭 WorkEntry (없으면 empty)
     */
    Optional<WorkEntry> findByEqpIdAndLotId(String eqpId, String lotId);

    /**
     * eqpId + lotId 기준으로 모든 WorkEntry를 반환합니다.
     *
     * @param eqpId 설비 ID
     * @param lotId lot ID
     * @return 매칭 WorkEntry 목록 (없으면 빈 리스트)
     */
    List<WorkEntry> findAllByEqpIdAndLotId(String eqpId, String lotId);

    /**
     * eqpId + carrierId 기준으로 첫 번째 WorkEntry를 반환합니다.
     *
     * @param eqpId     설비 ID
     * @param carrierId carrier ID
     * @return 매칭 WorkEntry (없으면 empty)
     */
    Optional<WorkEntry> findByEqpIdAndCarrierId(String eqpId, String carrierId);

    /**
     * eqpId + carrierId 기준으로 모든 WorkEntry를 반환합니다.
     *
     * @param eqpId     설비 ID
     * @param carrierId carrier ID
     * @return 매칭 WorkEntry 목록 (없으면 빈 리스트)
     */
    List<WorkEntry> findAllByEqpIdAndCarrierId(String eqpId, String carrierId);

    /**
     * eqpId + portId 기준으로 첫 번째 WorkEntry를 반환합니다.
     *
     * @param eqpId  설비 ID
     * @param portId port ID
     * @return 매칭 WorkEntry (없으면 empty)
     */
    Optional<WorkEntry> findByEqpIdAndPortId(String eqpId, String portId);

    /**
     * eqpId + portId 기준으로 모든 WorkEntry를 반환합니다.
     *
     * @param eqpId  설비 ID
     * @param portId port ID
     * @return 매칭 WorkEntry 목록 (없으면 빈 리스트)
     */
    List<WorkEntry> findAllByEqpIdAndPortId(String eqpId, String portId);
}

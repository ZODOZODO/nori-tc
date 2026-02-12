package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;

import java.util.List;
import java.util.Optional;

/**
 * 설비 정보를 제공하는 포트(인터페이스).
 *
 * - DB/Redis/외부 API 등 구체 구현과 분리하기 위한 계약
 * - 코어 로직은 이 인터페이스만 의존하고, 실제 구현은 어댑터에서 제공
 */
public interface EquipmentInfoProvider {

    /**
     * 전체 설비 목록 조회.
     *
     * - ACTIVE 연결 대상 탐색 등에 사용
     */
    List<GatewayEquipmentInfo> findAll();

    /**
     * eqpId로 설비 정보 조회.
     *
     * - PASSIVE 바인딩 시 검증/프로필 생성에 사용
     */
    Optional<GatewayEquipmentInfo> findById(String equipmentId);
}

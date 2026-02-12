package com.nori.tc.comm.gateway.context;

import java.util.List;
import java.util.Optional;

/**
 * EquipmentContextProfile 조회 포트입니다.
 *
 * <p>DB/Redis/외부 API 등 실제 데이터 소스 구현과 코어 로직을 분리하기 위한 추상화입니다.</p>
 */
public interface EquipmentContextProfileProvider {

    /**
     * 전체 설비 프로파일을 조회합니다.
     */
    List<EquipmentContextProfile> findAllProfiles();

    /**
     * eqpId로 단일 설비 프로파일을 조회합니다.
     */
    Optional<EquipmentContextProfile> findProfileById(String eqpId);
}


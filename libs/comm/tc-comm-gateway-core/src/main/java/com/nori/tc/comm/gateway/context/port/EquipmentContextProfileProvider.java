package com.nori.tc.comm.gateway.context.port;

import com.nori.tc.comm.gateway.context.model.EquipmentContextProfile;

import java.util.List;
import java.util.Optional;

/**
 * EquipmentContextProfile 조회 포트입니다.
 *
 * <p>이 인터페이스는 3차 구조 분해 기준에서 {@code context.port} 계층에 위치하며,
 * 컨텍스트 프로파일 조회 책임만 노출합니다.</p>
 * <p>DB/Redis/외부 API 등 실제 데이터 소스 구현과 코어 로직을 분리하기 위한 추상화입니다.</p>
 */
public interface EquipmentContextProfileProvider {

    /**
     * 전체 설비 프로파일을 조회합니다.
     *
     * <p>부팅 시 초기 컨텍스트 적재 또는 운영 중 전체 동기화 시점에 사용됩니다.</p>
     */
    List<EquipmentContextProfile> findAllProfiles();

    /**
     * eqpId로 단일 설비 프로파일을 조회합니다.
     *
     * <p>특정 설비 갱신/재적재 경로에서 사용됩니다.</p>
     */
    Optional<EquipmentContextProfile> findProfileById(String eqpId);
}

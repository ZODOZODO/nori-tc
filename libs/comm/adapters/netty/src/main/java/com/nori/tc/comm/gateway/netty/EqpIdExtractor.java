package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

import java.util.Optional;

/**
 * 등록 메시지(eqpid) 추출기 공통 인터페이스.
 *
 * 계약
 * - 등록 성공 시 Optional.of(eqpId) 반환
 * - 등록 실패(아직 데이터 부족/등록 메시지 아님) 시 Optional.empty()
 *
 * 중요: UNBOUND 단계에서 "등록 메시지 외의 프레임"은 처리 대상이 아니므로
 *      extractor 구현체는 해당 프레임을 버퍼에서 소비(discard)하여 드롭해야 합니다.
 */
public interface EqpIdExtractor {

    /**
     * ReassemblyBuffer에서 eqpId 등록 메시지를 추출합니다.
     *
     * @param buffer reassembly buffer (UNBOUND 단계 전용)
     * @return eqpId if found, otherwise empty
     */
    Optional<String> tryExtractEqpId(ReassemblyBuffer buffer);
}

package com.nori.tc.ui.core.port.db;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.ui.core.model.PagedResponse;

import java.util.Optional;

/**
 * 설비(EQP) 조회 전용 DB 접근 포트입니다.
 *
 * <p>본 포트는 UI 관리 페이지의 읽기 API와 직접 연결됩니다.</p>
 * <ul>
 *   <li>{@code GET /api/eqp}      : 설비 목록 조회</li>
 *   <li>{@code GET /api/eqp/{eqpId}} : 설비 상세 조회</li>
 * </ul>
 *
 * <p>중요 제약:</p>
 * <ul>
 *   <li>명령성 API(POST/PUT/DELETE/start/end)는 이 포트를 사용하지 않습니다.</li>
 *   <li>조회 기준의 SSOT는 {@code tc_eqp} 테이블이며, 구현체는 DB Store 계층을 통해 접근해야 합니다.</li>
 * </ul>
 */
public interface EqpQueryPort {

    /**
     * 설비 목록을 페이지 단위로 조회합니다.
     *
     * @param pageRequest offset/limit 기반 페이지 요청
     * @return 페이지 응답(목록 + 전체 건수)
     */
    PagedResponse<TcEqp> findAll(PageRequest pageRequest);

    /**
     * 설비 식별자(eqpId)로 단건을 조회합니다.
     *
     * @param eqpId 설비 비즈니스 키
     * @return 조회 결과, 미존재 시 빈 Optional
     */
    Optional<TcEqp> findByEqpId(String eqpId);
}

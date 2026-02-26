package com.nori.tc.db.core.eqp.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.domain.eqp.TcEqp;

/**
 * tc_eqp CRUD 인터페이스입니다.
 *
 * <p>역할:</p>
 * <p>- {@code tc_eqp} 테이블에 대한 저장/조회/삭제 포트를 정의합니다.</p>
 * <p>- 구현체(MyBatis/JPA)는 이 인터페이스를 기준으로 동일한 동작 계약을 제공해야 합니다.</p>
 *
 * <p>설계 주의사항:</p>
 * <p>- {@code comm_mode}, {@code route_partition}는 Gateway 라우팅/기동에 직접 사용되는 핵심 컬럼입니다.</p>
 * <p>- {@code deleteByEqpId}는 하위 1:1 테이블들이 ON DELETE CASCADE이므로 연쇄 삭제됩니다.</p>
 */
public interface TcEqpStore {

    /**
     * tc_eqp 행의 저장/갱신(upsert)을 처리합니다.
     *
     * <p>호출 시점:</p>
     * <p>- 설비 생성/수정 UI 처리</p>
     * <p>- 초기 데이터 적재/동기화 처리</p>
     *
     * <p>주요 부작용:</p>
     * <p>- {@code tc_eqp} 레코드 INSERT 또는 UPDATE가 발생할 수 있습니다.</p>
     *
     * @param command 저장/갱신할 tc_eqp 입력 모델 (comm_mode, route_partition 포함)
     * @return 저장 후 tc_eqp 최신 상태
     */
    TcEqp upsert(UpsertTcEqp command);

    /**
     * eqpId(비즈니스 키)로 tc_eqp 단건을 조회합니다.
     *
     * <p>호출 시점:</p>
     * <p>- Gateway/Business가 특정 설비 설정을 조회해야 하는 경우</p>
     * <p>- UI 요청 처리 중 단건 검증/조회가 필요한 경우</p>
     *
     * @param eqpId 설비 식별자(비즈니스 키)
     * @return 조회 결과(Optional). 없으면 {@link Optional#empty()}
     */
    Optional<TcEqp> findByEqpId(String eqpId);

    /**
     * tc_eqp 전체 목록을 페이징 조회합니다.
     *
     * <p>주의:</p>
     * <p>- 전체 조회는 데이터가 많을 수 있으므로 반드시 페이징으로 사용해야 합니다.</p>
     * <p>- Gateway 고정 라우팅 로딩에는 후속 추가 메서드
     *   {@link #findAllByRoutePartitionsAndEnabled(List, boolean, PageRequest)} 사용을 권장합니다.</p>
     *
     * @param page 페이징 조건
     * @return 조회 결과 목록
     */
    List<TcEqp> findAll(PageRequest page);

    /**
     * route_partition + enabled 조건으로 tc_eqp를 페이징 조회합니다.
     *
     * <p>주요 사용처:</p>
     * <p>- Gateway 기동 시 owned partition 대상 설비만 선별 로딩하는 경로</p>
     * <p>- Gateway 재동기화 시 특정 partition 범위의 활성/비활성 설비 조회</p>
     *
     * <p>필터 의미:</p>
     * <p>- SQL 의미는 {@code route_partition IN (...) AND enabled = ?} 입니다.</p>
     * <p>- {@code routePartitions}가 비어 있으면 구현체는 빈 결과를 반환하는 동작을 권장합니다.</p>
     * <p>- {@code routePartitions}에 중복 값이 있어도 결과는 중복 없이 조회되어야 합니다. (DB IN 절 기준)</p>
     *
     * <p>반환 정렬:</p>
     * <p>- 기본적으로 정렬 비보장을 권장하며, 필요 시 구현체 문서/코드에서 명시적으로 정렬 규칙을 정의합니다.</p>
     *
     * @param routePartitions 조회 대상 route_partition 목록
     * @param enabled enabled 필터 값
     * @param page 페이징 조건
     * @return 조건에 일치하는 tc_eqp 목록
     */
    List<TcEqp> findAllByRoutePartitionsAndEnabled(List<Integer> routePartitions, boolean enabled, PageRequest page);

    /**
     * eqpId 기준으로 tc_eqp를 삭제합니다.
     *
     * <p>부작용:</p>
     * <p>- 하위 1:1 테이블(tc_eqp_hsms, tc_eqp_socket 등)은 FK의 ON DELETE CASCADE에 의해 연쇄 삭제됩니다.</p>
     *
     * @param eqpId 삭제 대상 설비 식별자(비즈니스 키)
     */
    void deleteByEqpId(String eqpId);
}

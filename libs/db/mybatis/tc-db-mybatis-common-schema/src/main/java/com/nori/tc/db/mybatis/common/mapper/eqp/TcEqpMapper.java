package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqp;

/**
 * tc_eqp Mapper (FIX)
 *
 * - eqp_id는 UNIQUE 키이므로 insert/update를 분리 제공한다.
 * - upsert(벤더별 문법)는 starter/adapter에서 흡수하는 쪽이 안전하다.
 * - [2024-XX-XX FIX] 메모리 페이징 이슈 해결을 위해 findAll에 DB 페이징 파라미터 추가
 */
public interface TcEqpMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqp 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("e") TcEqp eqp);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqp 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("e") TcEqp eqp);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqp> findByEqpId(@Param("eqpId") String eqpId);

    /**
     * 페이징 검색 (FIX)
     * - 기존: 메모리로 다 가져와서 자름 (OOM 위험)
     * - 변경: DB에서 잘라오도록 offset/limit 추가
     *
     * @param offset 건너뛸 행 수
     * @param limit 가져올 행 수
     */
    List<TcEqp> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * route_partition + enabled 조건으로 설비 목록을 페이징 조회합니다.
     *
     * <p>주요 사용처:</p>
     * <p>- Gateway 기동 시 owned partition 대상 설비만 선별 로딩하는 경로</p>
     * <p>- Gateway 재동기화/재로딩 시 특정 partition 범위의 활성 설비 조회</p>
     *
     * <p>주의사항:</p>
     * <p>- {@code routePartitions}가 비어 있는 경우 호출 상위(Store)에서 빈 결과로 단락 처리하는 것을 권장합니다.</p>
     * <p>- 본 메서드는 SQL 매핑 계층이므로 입력 검증보다 파라미터 전달/매핑 정확성을 우선합니다.</p>
     *
     * @param routePartitions 조회 대상 route_partition 목록
     * @param enabled enabled 필터 값
     * @param offset 건너뛸 행 수
     * @param limit 가져올 행 수
     * @return 조건에 일치하는 설비 목록
     */
    List<TcEqp> findAllByRoutePartitionsAndEnabled(
            @Param("routePartitions") List<Integer> routePartitions,
            @Param("enabled") boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByEqpId(@Param("eqpId") String eqpId);
}

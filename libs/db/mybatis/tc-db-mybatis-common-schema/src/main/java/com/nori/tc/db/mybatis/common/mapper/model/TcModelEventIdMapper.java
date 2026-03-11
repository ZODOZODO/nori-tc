package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelEventId;

/**
 * tc_model_eventid Mapper (FIX)
 *
 * - (model_version_key, event_id) 조합이 유니크 키
 */
public interface TcModelEventIdMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelEventId 처리할 이벤트 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("e") TcModelEventId modelEventId);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelEventId 처리할 이벤트 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("e") TcModelEventId modelEventId);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelEventId> findByEventKey(@Param("eventKey") long eventKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param eventId 처리할 이벤트 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcModelEventId> findByModelVersionKeyAndEventId(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("eventId") String eventId
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcModelEventId> findAllByModelVersionKey(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByEventKey(@Param("eventKey") long eventKey);
}

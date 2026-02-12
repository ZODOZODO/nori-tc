package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;

/**
 * tc_work_processjob_lot_map Mapper.
 *
 * <p>
 * - PK: pj_lot_map_key (identity)
 * - Unique: (process_job_key, work_lot_key)
 * </p>
 */
public interface TcWorkProcessjobLotMapMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param map DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("m") TcWorkProcessjobLotMap map);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param map DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("m") TcWorkProcessjobLotMap map);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param pjLotMapKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkProcessjobLotMap> findByPjLotMapKey(@Param("pjLotMapKey") long pjLotMapKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @param workLotKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkProcessjobLotMap> findByProcessJobKeyAndWorkLotKey(
            @Param("processJobKey") long processJobKey,
            @Param("workLotKey") long workLotKey
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcWorkProcessjobLotMap> findAllByProcessJobKey(
            @Param("processJobKey") long processJobKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param pjLotMapKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByPjLotMapKey(@Param("pjLotMapKey") long pjLotMapKey);
}

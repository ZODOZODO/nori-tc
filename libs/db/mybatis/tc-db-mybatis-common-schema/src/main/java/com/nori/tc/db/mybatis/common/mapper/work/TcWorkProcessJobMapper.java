package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkProcessJob;

/**
 * tc_work_processjob Mapper (FIX)
 *
 * <p>
 * - Unique: (control_job_key, processjob_id)
 * - PK: process_job_key (identity)
 * </p>
 */
public interface TcWorkProcessJobMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJob DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("p") TcWorkProcessJob processJob);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJob DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("p") TcWorkProcessJob processJob);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkProcessJob> findByProcessJobKey(@Param("processJobKey") long processJobKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     * @param processjobId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkProcessJob> findByControlJobKeyAndProcessjobId(
            @Param("controlJobKey") long controlJobKey,
            @Param("processjobId") String processjobId
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcWorkProcessJob> findAllByControlJobKey(
            @Param("controlJobKey") long controlJobKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByProcessJobKey(@Param("processJobKey") long processJobKey);
}

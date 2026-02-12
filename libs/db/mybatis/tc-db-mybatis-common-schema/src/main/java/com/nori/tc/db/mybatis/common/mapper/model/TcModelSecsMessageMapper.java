package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelSecsMessage;

/**
 * tc_model_secs_message Mapper (FIX)
 *
 * - model_key + secs_msg_name 유니크
 */
public interface TcModelSecsMessageMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("m") TcModelSecsMessage message);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("m") TcModelSecsMessage message);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelSecsMessage> findBySecsMsgKey(@Param("secsMsgKey") long secsMsgKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param secsMsgName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelSecsMessage> findByModelKeyAndName(
            @Param("modelKey") long modelKey,
            @Param("secsMsgName") String secsMsgName
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcModelSecsMessage> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteBySecsMsgKey(@Param("secsMsgKey") long secsMsgKey);
}
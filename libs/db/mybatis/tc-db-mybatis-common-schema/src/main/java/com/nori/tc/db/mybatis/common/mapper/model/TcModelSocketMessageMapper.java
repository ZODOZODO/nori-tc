package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelSocketMessage;

/**
 * tc_model_socket_message Mapper (FIX)
 *
 * - Unique Key: (model_key, socket_msg_name)
 * - socket_msg_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 */
public interface TcModelSocketMessageMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketMessage 처리할 원본 데이터
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("s") TcModelSocketMessage socketMessage);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketMessage 처리할 원본 데이터
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("s") TcModelSocketMessage socketMessage);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcModelSocketMessage> findByModelKeySocketMsgName(
            @Param("modelKey") long modelKey,
            @Param("socketMsgName") String socketMsgName
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
    List<TcModelSocketMessage> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByModelKeySocketMsgName(
            @Param("modelKey") long modelKey,
            @Param("socketMsgName") String socketMsgName
    );
}

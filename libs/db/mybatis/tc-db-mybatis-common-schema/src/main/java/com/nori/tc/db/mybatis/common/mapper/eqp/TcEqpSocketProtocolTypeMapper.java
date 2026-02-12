package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;

/**
 * tc_eqp_socket_protocol_type Mapper (FIX)
 *
 * - 코드성 테이블이지만, 조회 시 리스트가 커질 수 있으므로 LIMIT/OFFSET을 강제합니다.
 */
public interface TcEqpSocketProtocolTypeMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param type DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("t") TcEqpSocketProtocolType type);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param type DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("t") TcEqpSocketProtocolType type);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpSocketProtocolType> findBySocketProtocolType(@Param("socketProtocolType") String socketProtocolType);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcEqpSocketProtocolType> findAll(@Param("offset") int offset, @Param("limit") int limit);

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteBySocketProtocolType(@Param("socketProtocolType") String socketProtocolType);
}

package com.nori.tc.db.mybatis.common.mapper.jar;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.jar.TcJarGateway;

/**
 * tc_jar_gateway Mapper.
 *
 * - 1:1 테이블(PK=eqp_key)
 */
public interface TcJarGatewayMapper {

    /**
     * 단건 insert를 수행합니다.
     *
     * @param jarGateway 저장 대상 행
     * @return 반영 건수
     */
    int insert(@Param("j") TcJarGateway jarGateway);

    /**
     * 단건 update를 수행합니다.
     *
     * @param jarGateway 갱신 대상 행
     * @return 반영 건수
     */
    int update(@Param("j") TcJarGateway jarGateway);

    /**
     * eqp_key 기준 단건 조회를 수행합니다.
     *
     * @param eqpKey 설비 키
     * @return 조회 결과(Optional)
     */
    Optional<TcJarGateway> findByEqpKey(@Param("eqpKey") long eqpKey);

    /**
     * eqp_key 기준 단건 삭제를 수행합니다.
     *
     * @param eqpKey 설비 키
     * @return 반영 건수
     */
    int deleteByEqpKey(@Param("eqpKey") long eqpKey);
}

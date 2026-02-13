package com.nori.tc.db.core.jar.store;

import java.util.Optional;

import com.nori.tc.db.core.jar.upsert.UpsertTcJarGateway;
import com.nori.tc.db.domain.jar.TcJarGateway;

/**
 * tc_jar_gateway CRUD 인터페이스.
 */
public interface TcJarGatewayStore {

    /**
     * tc_jar_gateway를 upsert 합니다.
     *
     * @param command upsert 입력 값
     * @return upsert 결과 행
     */
    TcJarGateway upsert(UpsertTcJarGateway command);

    /**
     * eqp_key 기준으로 단건을 조회합니다.
     *
     * @param eqpKey 설비 키(PK/FK)
     * @return 조회 결과(Optional)
     */
    Optional<TcJarGateway> findByEqpKey(long eqpKey);

    /**
     * eqp_key 기준으로 단건을 삭제합니다.
     *
     * @param eqpKey 설비 키(PK/FK)
     */
    void deleteByEqpKey(long eqpKey);
}

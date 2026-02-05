package com.nori.tc.db.core.eqp;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;

/**
 * tc_eqp_socket_protocol_type CRUD 인터페이스.
 *
 * 특징:
 * - 코드값(프로토콜 타입) 관리용 테이블로, 조회(목록 포함) 사용 빈도가 높습니다.
 * - 삭제 시 tc_eqp_socket FK 제약에 주의해야 합니다.
 */
public interface TcEqpSocketProtocolTypeStore {

    TcEqpSocketProtocolType upsert(UpsertTcEqpSocketProtocolType command);

    Optional<TcEqpSocketProtocolType> findBySocketProtocolType(String socketProtocolType);

    List<TcEqpSocketProtocolType> findAll(PageRequest page);

    void deleteBySocketProtocolType(String socketProtocolType);
}

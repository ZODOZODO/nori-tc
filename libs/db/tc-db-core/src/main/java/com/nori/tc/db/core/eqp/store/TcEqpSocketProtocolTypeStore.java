package com.nori.tc.db.core.eqp.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocketProtocolType;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;

/**
 * tc_eqp_socket_protocol_type CRUD 인터페이스.
 *
 * 특징:
 * - 코드값(프로토콜 타입) 관리용 테이블로, 조회(목록 포함) 사용 빈도가 높습니다.
 * - 삭제 시 tc_eqp_socket FK 제약에 주의해야 합니다.
 */
public interface TcEqpSocketProtocolTypeStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcEqpSocketProtocolType upsert(UpsertTcEqpSocketProtocolType command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpSocketProtocolType> findBySocketProtocolType(String socketProtocolType);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcEqpSocketProtocolType> findAll(PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param socketProtocolType 통신 채널/세션 정보
     */
    void deleteBySocketProtocolType(String socketProtocolType);
}

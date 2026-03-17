package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMesMessage;
import com.nori.tc.db.domain.model.TcModelMesMessage;

/**
 * tc_model_mes_message CRUD 인터페이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 *
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 */
public interface TcModelMesMessageStore {

    /**
     * MES 메시지 upsert.
     *
     * <p>
     * - mes_msg_key가 있으면 해당 PK 기반으로 갱신합니다.
     * - mes_msg_key가 없으면 (model_version_key, mes_msg_name) 유니크 키 기준으로
     * 존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
     * </p>
     *
     * @return upsert 후 상태의 TcModelMesMessage
     */
    TcModelMesMessage upsert(UpsertTcModelMesMessage command);

    /**
     * PK로 MES 메시지 단건 조회.
     *
     * @param mesMsgKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMesMessage> findByMesMsgKey(long mesMsgKey);

    /**
     * (model_version_key, mes_msg_name) 유니크 키로 단건 조회.
     *
     * @param modelVersionKey 대상 모델 버전 키
     * @param mesMsgName      MES 메시지 이름
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMesMessage> findByModelVersionKeyAndName(long modelVersionKey, String mesMsgName);

    /**
     * 특정 모델(model_version_key)의 MES 메시지 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelMesMessage> findAllByModelVersionKey(long modelVersionKey, PageRequest page);

    /**
     * PK로 MES 메시지 삭제.
     *
     * @param mesMsgKey 대상 키 값
     */
    void deleteByMesMsgKey(long mesMsgKey);
}

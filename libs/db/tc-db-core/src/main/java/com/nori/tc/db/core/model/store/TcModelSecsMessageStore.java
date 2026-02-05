package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.model.NewTcModelSecsMessage;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;

/**
 * tc_model_secs_message CRUD 인터페이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 *
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 */
public interface TcModelSecsMessageStore {

    /**
     * SECS 메시지 생성.
     *
     * @return DB가 부여한 secs_msg_key 및 timestamp가 채워진 TcModelSecsMessage
     */
    TcModelSecsMessage create(NewTcModelSecsMessage command);

    /**
     * SECS 메시지 갱신.
     *
     * @return 갱신 후 상태의 TcModelSecsMessage
     */
    TcModelSecsMessage update(UpsertTcModelSecsMessage command);

    Optional<TcModelSecsMessage> findBySecsMsgKey(long secsMsgKey);

    Optional<TcModelSecsMessage> findByModelKeyAndName(long modelKey, String secsMsgName);

    List<TcModelSecsMessage> findByModelKey(long modelKey);

    void deleteBySecsMsgKey(long secsMsgKey);
}

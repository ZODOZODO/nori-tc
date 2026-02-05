package com.nori.tc.db.core.model;

import java.util.Optional;

import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;

/**
 * tc_model_eventid CRUD 인터페이스.
 *
 * - (model_key, event_id) 유니크 키를 기준으로 upsert합니다.
 */
public interface TcModelEventIdStore {

    TcModelEventId upsert(UpsertTcModelEventId command);

    Optional<TcModelEventId> findByEventKey(long eventKey);

    Optional<TcModelEventId> findByModelKeyAndEventId(long modelKey, String eventId);

    void deleteByEventKey(long eventKey);
}

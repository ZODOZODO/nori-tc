package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;

/**
 * tc_model_socket_message CRUD 인터페이스.
 */
public interface TcModelSocketMessageStore {

    TcModelSocketMessage upsert(UpsertTcModelSocketMessage command);

    Optional<TcModelSocketMessage> findByModelKeySocketMsgName(long modelKey, String socketMsgName);

    List<TcModelSocketMessage> findAllByModelKey(long modelKey, PageRequest page);

    void deleteByModelKeySocketMsgName(long modelKey, String socketMsgName);
}

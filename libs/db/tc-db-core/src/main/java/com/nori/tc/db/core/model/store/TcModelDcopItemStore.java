package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.TcModelDcopItemSearchCriteria;
import com.nori.tc.db.core.model.upsert.UpsertTcModelDcopItem;
import com.nori.tc.db.domain.model.TcModelDcopItem;

/**
 * tc_model_dcop_item CRUD 인터페이스.
 *
 * - Unique(model_key, dcop_item_name)을 기준으로 upsert/조회/삭제를 수행한다.
 */
public interface TcModelDcopItemStore {

    TcModelDcopItem upsert(UpsertTcModelDcopItem command);

    Optional<TcModelDcopItem> findByModelKeyAndName(long modelKey, String dcopItemName);

    List<TcModelDcopItem> findAll(TcModelDcopItemSearchCriteria criteria, PageRequest page);

    void deleteByModelKeyAndName(long modelKey, String dcopItemName);
}

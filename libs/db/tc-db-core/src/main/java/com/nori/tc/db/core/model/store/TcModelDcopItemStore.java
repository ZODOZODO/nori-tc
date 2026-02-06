package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
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

    /**
     * 특정 모델(model_key)의 DCOP 아이템 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelDcopItem> findAllByModelKey(long modelKey, PageRequest page);

    void deleteByModelKeyAndName(long modelKey, String dcopItemName);
}
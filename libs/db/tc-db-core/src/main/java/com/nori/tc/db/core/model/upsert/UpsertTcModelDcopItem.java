package com.nori.tc.db.core.model.upsert;

import com.nori.tc.db.domain.common.model.DcopCalculationRule;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;

/**
 * tc_model_dcop_item upsert 입력(Command)
 *
 * - (modelKey, dcopItemName)이 유니크 키이므로 upsert 기준 키로 사용한다.
 * - updatedAt은 DB가 관리하는 것을 권장한다.
 */
public record UpsertTcModelDcopItem(
        long modelKey,
        String dcopItemName,
        String workflowName,
        String eventId,
        String variableId,
        DcopCollectionRule collectionRule,
        DcopCalculationRule calculationRule,
        Integer orderRule
) {
}

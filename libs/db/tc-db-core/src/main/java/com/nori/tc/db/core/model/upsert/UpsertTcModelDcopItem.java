package com.nori.tc.db.core.model.upsert;

import com.nori.tc.db.domain.common.model.DcopCollectionRule;

/**
 * tc_model_dcop_item upsert 입력(Command)
 *
 * - (modelVersionKey, dcopItemName)이 유니크 키이므로 upsert 기준 키로 사용한다.
 * - updatedAt은 DB가 관리하는 것을 권장한다.
 * - calculationRule은 자유 텍스트(varchar 2000)로 저장한다.
 */
public record UpsertTcModelDcopItem(
        long modelVersionKey,
        String dcopItemName,
        String workflowName,
        String eventId,
        String variableId,
        DcopCollectionRule collectionRule,
        String calculationRule,
        Integer orderRule
) {

    public UpsertTcModelDcopItem {
        ModelFieldLengthValidator.requireTextWithMax(
                dcopItemName,
                "dcopItemName",
                ModelFieldLengthValidator.DCOP_ITEM_NAME_MAX_LENGTH
        );
        ModelFieldLengthValidator.validateOptionalTextMax(
                workflowName,
                "workflowName",
                ModelFieldLengthValidator.WORKFLOW_NAME_MAX_LENGTH
        );
        ModelFieldLengthValidator.validateOptionalTextMax(
                variableId,
                "variableId",
                ModelFieldLengthValidator.VARIABLE_ID_MAX_LENGTH
        );
    }
}

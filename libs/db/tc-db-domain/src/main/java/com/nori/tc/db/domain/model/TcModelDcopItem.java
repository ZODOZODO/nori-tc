package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.model.DcopCalculationRule;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;

/**
 * tc_model_dcop_item 테이블 1행에 대응하는 순수 DTO.
 *
 * [DB 스키마 요약]
 * - dcop_item_key    : bigint PK (IDENTITY)
 * - model_key        : bigint NOT NULL (FK -> tc_model.model_key)
 * - dcop_item_name   : varchar(200) NOT NULL
 * - workflow_name    : varchar(200) NULL
 * - event_id         : varchar(100) NULL
 * - variable_id      : varchar(100) NULL
 * - collection_rule  : varchar(10) NULL (FIRST/LAST)
 * - calculation_rule : varchar(20) NULL (ADD/MULTIPLY/SUBTRACT/NONE)
 * - order_rule       : int NULL (>= 0)
 * - updated_at       : timestamptz NOT NULL default CURRENT_TIMESTAMP
 *
 * [유니크 제약]
 * - UNIQUE (model_key, dcop_item_name)
 */
public record TcModelDcopItem(
        Long dcopItemKey,
        long modelKey,
        String dcopItemName,
        String workflowName,
        String eventId,
        String variableId,
        DcopCollectionRule collectionRule,
        DcopCalculationRule calculationRule,
        Integer orderRule,
        OffsetDateTime updatedAt
) {
}

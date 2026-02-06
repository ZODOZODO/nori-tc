package com.nori.tc.db.domain.common.model;

/**
 * DCOP 수집 규칙 (tc_model_dcop_item.collection_rule)
 *
 * DB Check Constraint:
 * - FIRST
 * - LAST
 *
 * 주의:
 * - null 가능 컬럼이므로, 도메인에서도 null 허용을 기본으로 한다.
 * - 값은 Adapter(JPA/MyBatis) 계층에서 EnumTypeHandler로 매핑한다.
 */
public enum DcopCollectionRule {
    FIRST,
    LAST
}

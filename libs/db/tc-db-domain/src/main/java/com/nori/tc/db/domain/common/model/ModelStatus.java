package com.nori.tc.db.domain.common.model;

/**
 * 모델 상태 (tc_model.status)
 *
 * DB Check Constraint:
 * - DRAFT
 * - ACTIVE
 * - DEPRECATED
 */
public enum ModelStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED
}

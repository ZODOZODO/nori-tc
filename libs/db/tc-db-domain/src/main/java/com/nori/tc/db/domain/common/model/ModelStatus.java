package com.nori.tc.db.domain.common.model;

/**
 * 모델 상태 (tc_model.status)
 *
 * DB Check Constraint:
 * - DEVELOP
 * - OPERATE
 * - DEPRECATED
 */
public enum ModelStatus {
    DEVELOP,
    OPERATE,
    DEPRECATED
}

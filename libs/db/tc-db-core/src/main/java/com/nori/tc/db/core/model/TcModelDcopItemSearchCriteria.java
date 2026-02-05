package com.nori.tc.db.core.model;

/**
 * tc_model_dcop_item 검색 조건.
 *
 * - modelKey: 필수 (해당 모델의 항목만 조회)
 */
public record TcModelDcopItemSearchCriteria(
        long modelKey
) {
    public static TcModelDcopItemSearchCriteria of(long modelKey) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        return new TcModelDcopItemSearchCriteria(modelKey);
    }
}

package com.nori.tc.db.core.model;

import com.nori.tc.db.domain.common.ModelStatus;
import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_model 검색 조건(Criteria)
 *
 * - null은 "조건 없음"을 의미합니다.
 * - LIKE 검색의 구체 규칙(contains/startsWith)은 구현체에서 문서화/고정합니다.
 * - commInterface는 tc_model.comm_interface(HSMS/SOCKET) 기준 필터입니다.
 */
public record TcModelSearchCriteria(
        String modelNameLike,
        ProtocolType commInterface,
        ModelStatus status
) {
    public static TcModelSearchCriteria empty() {
        return new TcModelSearchCriteria(null, null, null);
    }
}

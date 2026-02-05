package com.nori.tc.db.core.eqp;

import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_eqp 검색 조건(Criteria)
 *
 * - null은 "조건 없음"을 의미합니다.
 */
public record TcEqpSearchCriteria(
        ProtocolType commInterface,
        Boolean enabled
) {
    public static TcEqpSearchCriteria empty() {
        return new TcEqpSearchCriteria(null, null);
    }
}

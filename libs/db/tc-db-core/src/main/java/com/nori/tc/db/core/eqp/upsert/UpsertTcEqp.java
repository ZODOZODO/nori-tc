package com.nori.tc.db.core.eqp.upsert;

import com.nori.tc.db.domain.common.model.ProtocolType;

/**
 * tc_eqp upsert 입력(Command)
 *
 * - eqp_id는 UNIQUE 키이므로 "생성/수정"을 구분하기보다 upsert가 실용적입니다.
 * - commInterface는 DB의 comm_interface 컬럼(HSMS/SOCKET)을 의미합니다.
 * - created_at/updated_at은 DB(또는 구현체)가 관리하는 것을 권장합니다.
 * - createdBy/updatedBy는 null이면 DB default(SYSTEM) 또는 구현체 기본값으로 대체됩니다.
 */
public record UpsertTcEqp(
        String eqpId,
        ProtocolType commInterface,
        String eqpIp,
        int eqpPort,
        long modelKey,
        boolean enabled,
        String createdBy,
        String updatedBy
) {
}

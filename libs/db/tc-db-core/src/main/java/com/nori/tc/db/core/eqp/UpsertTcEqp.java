package com.nori.tc.db.core.eqp;

import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_eqp upsert 입력(Command)
 *
 * - tc_eqp는 eqp_id가 PK이므로 "생성/수정"을 구분하기보다 upsert가 실용적입니다.
 * - created_at/updated_at은 DB(또는 구현체)가 관리하는 것을 권장합니다.
 */
public record UpsertTcEqp(
        String eqpId,
        ProtocolType protocolType,
        String eqpIp,
        int eqpPort,
        long modelKey,
        boolean enabled
) {
}

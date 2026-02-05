package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

/**
 * tc_eqp_global upsert 입력(Command)
 *
 * - eqpKey/paramName 조합이 유니크 키이므로, 두 값이 upsert 기준 키가 된다.
 * - updatedAt은 MyBatis 등 비-JPA 환경에서 필요할 수 있어 포함한다.
 */
public record UpsertTcEqpGlobal(
        long eqpKey,
        String paramName,
        String paramValue,
        OffsetDateTime updatedAt
) {
}

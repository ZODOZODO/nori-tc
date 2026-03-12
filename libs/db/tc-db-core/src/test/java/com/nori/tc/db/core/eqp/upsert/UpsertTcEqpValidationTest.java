package com.nori.tc.db.core.eqp.upsert;

import com.nori.tc.db.domain.common.model.ProtocolType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * tc_eqp upsert command의 문자열 길이 계약을 검증합니다.
 */
class UpsertTcEqpValidationTest {

    @Test
    @DisplayName("tc_eqp upsert command는 appliedParamVersion 100자를 허용합니다")
    void allowsAppliedParamVersionUpTo100Characters() {
        assertDoesNotThrow(() -> new UpsertTcEqp(
                "EQP-01",
                ProtocolType.SECS,
                "ACTIVE",
                false,
                1,
                "127.0.0.1",
                5000,
                101L,
                repeat('V', 100),
                true,
                "SYSTEM",
                "SYSTEM"
        ));
    }

    @Test
    @DisplayName("tc_eqp upsert command는 appliedParamVersion 100자 초과를 차단합니다")
    void rejectsAppliedParamVersionOver100Characters() {
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcEqp(
                "EQP-01",
                ProtocolType.SECS,
                "ACTIVE",
                false,
                1,
                "127.0.0.1",
                5000,
                101L,
                repeat('V', 101),
                true,
                "SYSTEM",
                "SYSTEM"
        ));
    }

    private static String repeat(final char value, final int count) {
        return String.valueOf(value).repeat(count);
    }
}

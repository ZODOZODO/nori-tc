package com.nori.tc.db.core.eqp.upsert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * tc_eqp_param_version upsert command의 문자열 길이 계약을 검증합니다.
 */
class UpsertTcEqpParamVersionValidationTest {

    @Test
    @DisplayName("tc_eqp_param_version upsert command는 versionDescription 2000자를 허용합니다")
    void allowsVersionDescriptionUpTo2000Characters() {
        assertDoesNotThrow(() -> new UpsertTcEqpParamVersion(
                1L,
                "25.03.12.0000",
                "D".repeat(2000),
                "SYSTEM",
                "SYSTEM"
        ));
    }

    @Test
    @DisplayName("tc_eqp_param_version upsert command는 versionDescription 2000자 초과를 차단합니다")
    void rejectsVersionDescriptionOver2000Characters() {
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcEqpParamVersion(
                1L,
                "25.03.12.0000",
                "D".repeat(2001),
                "SYSTEM",
                "SYSTEM"
        ));
    }
}

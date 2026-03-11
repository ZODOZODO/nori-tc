package com.nori.tc.ui.adapter.db;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.domain.common.eqp.ControlState;
import com.nori.tc.db.domain.common.eqp.EqpState;
import com.nori.tc.db.domain.common.model.ProtocolType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link JpaEqpCrudPort}의 EQP 생성 초기 상태 규칙을 검증합니다.
 */
class JpaEqpCrudPortTest {

    @Test
    @DisplayName("SECS EQP 생성 초기 상태는 DOWN/DOWN으로 생성됩니다")
    void createInitialStateCommandForSecsUsesDownDown() {
        final OffsetDateTime fixedTimestamp = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");

        final UpsertTcEqpState command = JpaEqpCrudPort.createInitialStateCommand(
                100L,
                ProtocolType.SECS,
                fixedTimestamp
        );

        assertEquals(100L, command.eqpKey());
        assertEquals(ControlState.DOWN, command.controlState());
        assertEquals(EqpState.DOWN, command.eqpState());
        assertEquals(fixedTimestamp, command.sinceAt());
        assertEquals("EQP_CREATED", command.reasonCode());
        assertEquals("EQP created by UI management service", command.reasonDetail());
        assertEquals(fixedTimestamp, command.updatedAt());
    }

    @Test
    @DisplayName("SOCKET EQP 생성 초기 상태는 DISCONNECTED/SERVICE_UNAVAILABLE로 생성됩니다")
    void createInitialStateCommandForSocketUsesDisconnectedServiceUnavailable() {
        final OffsetDateTime fixedTimestamp = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");

        final UpsertTcEqpState command = JpaEqpCrudPort.createInitialStateCommand(
                200L,
                ProtocolType.SOCKET,
                fixedTimestamp
        );

        assertEquals(200L, command.eqpKey());
        assertEquals(ControlState.DISCONNECTED, command.controlState());
        assertEquals(EqpState.SERVICE_UNAVAILABLE, command.eqpState());
        assertEquals(fixedTimestamp, command.sinceAt());
        assertEquals("EQP_CREATED", command.reasonCode());
        assertEquals("EQP created by UI management service", command.reasonDetail());
        assertEquals(fixedTimestamp, command.updatedAt());
    }
}

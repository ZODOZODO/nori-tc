package com.nori.tc.ui.adapter.db;

import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.ui.core.exception.EqpAlreadyCheckedOutException;
import com.nori.tc.ui.core.port.db.EqpParamCommandPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JpaEqpParamCommandPort}의 checkout 경쟁 제어와 충돌 메시지 정규화를 검증합니다.
 */
class JpaEqpParamCommandPortTest {

    @Test
    @DisplayName("checkout은 설비 잠금 조회 뒤 source version을 EDIT로 복사합니다")
    void checkoutUsesLockedEqpLookup() {
        final Fixture fixture = new Fixture();
        final TcEqp eqp = eqp(1L, "EQP-01");
        final TcEqpParam sourceParam = param(11L, eqp.eqpKey(), "TEMP", "v1", "100", "source", "SYSTEM");
        final TcEqpParam editParam = param(12L, eqp.eqpKey(), "TEMP", "EDIT", "100", "source", "tester");

        when(fixture.eqpStore.findByEqpIdForUpdate("EQP-01")).thenReturn(Optional.of(eqp));
        when(fixture.eqpParamStore.findAllByEqpKeyAndVersion(eqp.eqpKey(), "v1")).thenReturn(List.of(sourceParam));
        when(fixture.eqpParamStore.findAllByEqpKeyAndVersion(eqp.eqpKey(), "EDIT")).thenReturn(List.of(editParam));

        final List<EqpParamCommandPort.EqpParamView> result = fixture.port.checkout("EQP-01", "v1", "tester");

        verify(fixture.eqpStore).findByEqpIdForUpdate("EQP-01");
        verify(fixture.eqpStore, never()).findByEqpId("EQP-01");
        verify(fixture.eqpParamStore).existsByEqpKeyAndVersion(eqp.eqpKey(), "EDIT");
        verify(fixture.eqpParamStore).upsert(any());
        assertEquals(1, result.size());
        assertEquals("TEMP", result.get(0).paramName());
        assertEquals("tester", result.get(0).createdBy());
    }

    @Test
    @DisplayName("checkout은 이미 EDIT가 있으면 현재 편집 사용자를 포함한 409 예외를 반환합니다")
    void checkoutWhenAlreadyCheckedOutReturnsNormalizedConflictMessage() {
        final Fixture fixture = new Fixture();
        final TcEqp eqp = eqp(1L, "EQP-01");

        when(fixture.eqpStore.findByEqpIdForUpdate("EQP-01")).thenReturn(Optional.of(eqp));
        when(fixture.eqpParamStore.existsByEqpKeyAndVersion(eqp.eqpKey(), "EDIT")).thenReturn(true);
        when(fixture.eqpParamStore.findAllByEqpKeyAndVersion(eqp.eqpKey(), "EDIT"))
                .thenReturn(List.of(param(21L, eqp.eqpKey(), "TEMP", "EDIT", "100", "editing", "alice")));

        final EqpAlreadyCheckedOutException exception = assertThrows(
                EqpAlreadyCheckedOutException.class,
                () -> fixture.port.checkout("EQP-01", "v1", "tester")
        );

        assertEquals("설비 파라미터가 이미 체크아웃 중입니다. 현재 편집 사용자: alice.", exception.getMessage());
    }

    @Test
    @DisplayName("checkout은 duplicate key 후 owner 재조회가 비어도 정규화된 409 예외를 반환합니다")
    void checkoutDuplicateKeyWithoutOwnerStillReturnsNormalizedConflictMessage() {
        final Fixture fixture = new Fixture();
        final TcEqp eqp = eqp(1L, "EQP-01");

        when(fixture.eqpStore.findByEqpIdForUpdate("EQP-01")).thenReturn(Optional.of(eqp));
        when(fixture.eqpParamStore.findAllByEqpKeyAndVersion(eqp.eqpKey(), "v1"))
                .thenReturn(List.of(param(31L, eqp.eqpKey(), "TEMP", "v1", "100", "source", "SYSTEM")));
        when(fixture.eqpParamStore.upsert(any()))
                .thenThrow(new DbDuplicateKeyException("duplicate", new RuntimeException("duplicate")));
        when(fixture.eqpParamStore.findAllByEqpKeyAndVersion(eqp.eqpKey(), "EDIT")).thenReturn(List.of());

        final EqpAlreadyCheckedOutException exception = assertThrows(
                EqpAlreadyCheckedOutException.class,
                () -> fixture.port.checkout("EQP-01", "v1", "tester")
        );

        assertEquals(
                "설비 파라미터가 이미 다른 사용자에 의해 체크아웃 중입니다. 잠시 후 다시 시도해 주세요.",
                exception.getMessage()
        );
    }

    /**
     * 테스트용 mock fixture입니다.
     */
    private static final class Fixture {

        private final TcEqpStore eqpStore = mock(TcEqpStore.class);
        private final TcEqpParamStore eqpParamStore = mock(TcEqpParamStore.class);
        private final JpaEqpParamCommandPort port = new JpaEqpParamCommandPort(eqpStore, eqpParamStore);
    }

    private static TcEqp eqp(final long eqpKey, final String eqpId) {
        final OffsetDateTime now = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");
        return new TcEqp(
                eqpKey,
                eqpId,
                ProtocolType.SECS,
                "ACTIVE",
                false,
                1,
                "127.0.0.1",
                5000,
                101L,
                "v1",
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    private static TcEqpParam param(
            final long eqpParamKey,
            final long eqpKey,
            final String paramName,
            final String paramVersion,
            final String paramValue,
            final String description,
            final String createdBy
    ) {
        return new TcEqpParam(
                eqpParamKey,
                eqpKey,
                paramName,
                paramVersion,
                paramValue,
                description,
                createdBy,
                OffsetDateTime.parse("2026-03-11T10:15:30+09:00")
        );
    }
}

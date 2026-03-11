package com.nori.tc.db.core.model.upsert;

import com.nori.tc.db.domain.common.model.DcopCalculationRule;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.common.model.VariableIdType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 모델 계열 upsert command의 문자열 길이 계약을 검증합니다.
 */
class ModelUpsertLengthValidationTest {

    @Test
    @DisplayName("tc_model upsert command는 확장된 modelName/modelVersion 길이를 허용합니다")
    void upsertTcModelAllowsExtendedLengths() {
        assertDoesNotThrow(() -> new UpsertTcModel(
                null,
                repeat('M', 1000),
                repeat('P', 1000),
                repeat('V', 100),
                ProtocolType.SECS,
                ModelStatus.OPERATE,
                "desc",
                "maker",
                "SYSTEM",
                "SYSTEM"
        ));
    }

    @Test
    @DisplayName("tc_model upsert command는 modelName/modelVersion 길이 초과를 차단합니다")
    void upsertTcModelRejectsExceededLengths() {
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModel(
                null,
                repeat('M', 1001),
                null,
                "EDIT",
                ProtocolType.SECS,
                ModelStatus.OPERATE,
                null,
                null,
                "SYSTEM",
                "SYSTEM"
        ));

        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModel(
                null,
                "MODEL",
                null,
                repeat('V', 101),
                ProtocolType.SECS,
                ModelStatus.OPERATE,
                null,
                null,
                "SYSTEM",
                "SYSTEM"
        ));
    }

    @Test
    @DisplayName("workflow/dcop/mdf/param/message/variable command는 확장된 길이를 허용합니다")
    void detailCommandsAllowExtendedLengths() {
        assertDoesNotThrow(() -> new UpsertTcModelWorkflow(
                null,
                1L,
                repeat('W', 1000),
                repeat('N', 1000),
                "EVT",
                repeat('T', 2000),
                repeat('F', 4000),
                "ACTION",
                "INDEX"
        ));
        assertDoesNotThrow(() -> new UpsertTcModelDcopItem(
                1L,
                repeat('D', 1000),
                repeat('W', 1000),
                "EVT",
                repeat('I', 1000),
                null,
                DcopCalculationRule.NONE,
                1
        ));
        assertDoesNotThrow(() -> new UpsertTcModelMdf(null, 1L, repeat('M', 1000), "<xml/>".getBytes()));
        assertDoesNotThrow(() -> new UpsertTcModelParam(1L, repeat('P', 1000), "value", null));
        assertDoesNotThrow(() -> new UpsertTcModelSecsMessage(null, 1L, repeat('S', 1000), null, null));
        assertDoesNotThrow(() -> new UpsertTcModelSocketMessage(1L, repeat('K', 1000), null, null, null));
        assertDoesNotThrow(() -> new UpsertTcModelVariableId(1L, VariableIdType.SVID, repeat('I', 1000), null));
    }

    @Test
    @DisplayName("workflow/dcop/mdf/param/message/variable command는 길이 초과를 차단합니다")
    void detailCommandsRejectExceededLengths() {
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelWorkflow(
                null,
                1L,
                repeat('W', 1001),
                "MSG",
                null,
                null,
                null,
                "ACTION",
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelWorkflow(
                null,
                1L,
                "FLOW",
                repeat('N', 1001),
                null,
                null,
                null,
                "ACTION",
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelWorkflow(
                null,
                1L,
                "FLOW",
                "MSG",
                null,
                repeat('T', 2001),
                null,
                "ACTION",
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelWorkflow(
                null,
                1L,
                "FLOW",
                "MSG",
                null,
                null,
                repeat('F', 4001),
                "ACTION",
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelDcopItem(
                1L,
                repeat('D', 1001),
                null,
                null,
                null,
                null,
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelMdf(null, 1L, repeat('M', 1001), "<xml/>".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelParam(1L, repeat('P', 1001), null, null));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelSecsMessage(null, 1L, repeat('S', 1001), null, null));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelSocketMessage(1L, repeat('K', 1001), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new UpsertTcModelVariableId(1L, VariableIdType.SVID, repeat('I', 1001), null));
    }

    private static String repeat(final char value, final int count) {
        return String.valueOf(value).repeat(count);
    }
}

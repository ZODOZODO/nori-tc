package com.nori.tc.messaging.domain.kafka;

import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.validation.KafkaMetadataValidator;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationErrorCode;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KafkaMetadataValidator 단위 테스트입니다.
 *
 * <p>metadata 2타입 분리 정책과 eventType/source/schemaVersion 검증 정책이
 * 의도대로 동작하는지 확인합니다.</p>
 */
class KafkaMetadataValidatorTest {

    /**
     * 검증 대상 metadata validator입니다.
     */
    private final KafkaMetadataValidator validator = new KafkaMetadataValidator();

    /**
     * MES metadata 정상 케이스를 검증합니다.
     */
    @Test
    @DisplayName("MES 토픽 + MES metadata 조합은 검증을 통과한다")
    void validateMesMetadataSuccess() {
        final TcMesKafkaMetadata metadata = new TcMesKafkaMetadata(
                "MES_LOT_COMPLETED",
                "2026-02-19T10:00:00Z",
                TcKafkaSources.EXTERNAL_MES_ADAPTER,
                "LOT-201",
                "v1"
        );

        final List<KafkaValidationFailure> failures = validator.validate(TcKafkaTopics.MES_EVENTS, metadata);
        assertTrue(failures.isEmpty());
    }

    /**
     * 비-MES metadata 정상 케이스를 검증합니다.
     */
    @Test
    @DisplayName("Gateway UI 이벤트 토픽 + Common metadata 조합은 검증을 통과한다")
    void validateCommonMetadataSuccess() {
        final TcCommonKafkaMetadata metadata = new TcCommonKafkaMetadata(
                "EQP_CREATE",
                "2026-02-19T10:00:00Z",
                TcKafkaSources.UI_BACKEND,
                "TRACE-100",
                "v1"
        );

        // U1 설계 기준:
        // UI 이벤트 토픽이 Gateway/Business로 분리되었으므로 Gateway 대상 토픽으로 검증합니다.
        final List<KafkaValidationFailure> failures = validator.validate(TcKafkaTopics.UI_EVENTS_GATEWAY, metadata);
        assertTrue(failures.isEmpty());
    }

    /**
     * eventType 네이밍 정책 위반 시 에러코드가 올바르게 반환되는지 검증합니다.
     */
    @Test
    @DisplayName("Business UI 이벤트 토픽에서 eventType 규칙 위반 시 INVALID_EVENT_TYPE이 반환된다")
    void validateFailsWhenEventTypeIsInvalid() {
        final TcCommonKafkaMetadata metadata = new TcCommonKafkaMetadata(
                "eqp.create",
                "2026-02-19T10:00:00Z",
                TcKafkaSources.UI_BACKEND,
                "TRACE-101",
                "v1"
        );

        // U1 설계 기준:
        // 분리된 Business 대상 UI 이벤트 토픽도 공통 metadata 정책 검증을 동일하게 적용합니다.
        final List<KafkaValidationFailure> failures = validator.validate(TcKafkaTopics.UI_EVENTS_BUSINESS, metadata);
        assertTrue(failures.stream().anyMatch(f -> f.errorCode() == KafkaValidationErrorCode.INVALID_EVENT_TYPE));
    }

    /**
     * 토픽 소유권 기준 source 검증 실패를 확인합니다.
     */
    @Test
    @DisplayName("토픽 allowlist와 다른 source면 SOURCE_NOT_ALLOWED가 반환된다")
    void validateFailsWhenSourceIsNotAllowed() {
        final TcCommonKafkaMetadata metadata = new TcCommonKafkaMetadata(
                "EQP_STATUS_CHANGED",
                "2026-02-19T10:00:00Z",
                TcKafkaSources.BUSINESS_CORE,
                "TRACE-102",
                "v1"
        );

        final List<KafkaValidationFailure> failures = validator.validate(TcKafkaTopics.EQP_EVENTS, metadata);
        assertTrue(failures.stream().anyMatch(f -> f.errorCode() == KafkaValidationErrorCode.SOURCE_NOT_ALLOWED));
    }

    /**
     * 스키마 버전 위반 시 에러코드를 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("허용되지 않은 schemaVersion이면 SCHEMA_VERSION_NOT_ALLOWED가 반환된다")
    void validateFailsWhenSchemaVersionIsNotAllowed() {
        final TcCommonKafkaMetadata metadata = new TcCommonKafkaMetadata(
                "EQP_STATUS_CHANGED",
                "2026-02-19T10:00:00Z",
                TcKafkaSources.COMM_GATEWAY,
                "TRACE-103",
                "v2"
        );

        final List<KafkaValidationFailure> failures = validator.validate(TcKafkaTopics.EQP_EVENTS, metadata);
        assertTrue(failures.stream().anyMatch(f -> f.errorCode() == KafkaValidationErrorCode.SCHEMA_VERSION_NOT_ALLOWED));
    }

    /**
     * MES 토픽에 Common metadata를 주입하면 타입 불일치 에러가 발생하는지 확인합니다.
     */
    @Test
    @DisplayName("MES 토픽에 Common metadata를 사용하면 타입 불일치로 실패한다")
    void validateFailsWhenMetadataTypeMismatched() {
        final TcCommonKafkaMetadata metadata = new TcCommonKafkaMetadata(
                "MES_LOT_STARTED",
                "2026-02-19T10:00:00Z",
                TcKafkaSources.EXTERNAL_MES_ADAPTER,
                "TRACE-104",
                "v1"
        );

        final List<KafkaValidationFailure> failures = validator.validate(TcKafkaTopics.MES_EVENTS, metadata);
        assertTrue(failures.stream().anyMatch(f -> f.errorCode() == KafkaValidationErrorCode.METADATA_TYPE_MISMATCH));
    }
}

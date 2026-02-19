package com.nori.tc.messaging.domain.kafka.policy;

import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationDisposition;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationErrorCode;
import com.nori.tc.messaging.domain.kafka.validation.KafkaValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * eventType 네이밍 규칙 정책입니다.
 *
 * <p>기본 규칙:
 * - 영문 대문자/숫자/언더스코어만 허용
 * - 첫 글자는 영문 대문자
 * - 단어 구분은 언더스코어</p>
 *
 * <p>예시: {@code EQP_CREATE}, {@code MES_LOT_COMPLETE}</p>
 */
public final class KafkaEventTypeNamingPolicy {

    /**
     * 기본 eventType 패턴입니다.
     */
    public static final Pattern DEFAULT_EVENT_TYPE_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$");

    private static final Logger log = LoggerFactory.getLogger(KafkaEventTypeNamingPolicy.class);

    /**
     * 실제 검증에 사용하는 정규식 패턴입니다.
     */
    private final Pattern eventTypePattern;

    /**
     * 기본 패턴을 사용하는 정책 객체를 생성합니다.
     */
    public KafkaEventTypeNamingPolicy() {
        this(DEFAULT_EVENT_TYPE_PATTERN);
    }

    /**
     * 사용자 정의 패턴으로 정책 객체를 생성합니다.
     *
     * @param eventTypePattern eventType 검증 패턴
     */
    public KafkaEventTypeNamingPolicy(final Pattern eventTypePattern) {
        this.eventTypePattern = Objects.requireNonNull(eventTypePattern, "eventTypePattern is required");
        log.info("KafkaEventTypeNamingPolicy initialized. pattern={}", eventTypePattern.pattern());
    }

    /**
     * eventType가 정책을 만족하는지 검증합니다.
     *
     * @param eventType 검증 대상 eventType
     * @return 실패 시 실패 정보, 성공 시 빈 Optional
     */
    public Optional<KafkaValidationFailure> validate(final String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.MISSING_EVENT_TYPE,
                    KafkaValidationDisposition.REJECTED,
                    "eventType is required"
            ));
        }

        final String normalizedEventType = eventType.trim();
        if (!eventTypePattern.matcher(normalizedEventType).matches()) {
            if (log.isDebugEnabled()) {
                log.debug("eventType naming validation failed. eventType={}, pattern={}",
                        normalizedEventType, eventTypePattern.pattern());
            }
            return Optional.of(new KafkaValidationFailure(
                    KafkaValidationErrorCode.INVALID_EVENT_TYPE,
                    KafkaValidationDisposition.REJECTED,
                    "eventType naming policy violation: " + normalizedEventType
            ));
        }

        if (log.isDebugEnabled()) {
            log.debug("eventType naming validation passed. eventType={}", normalizedEventType);
        }
        return Optional.empty();
    }
}


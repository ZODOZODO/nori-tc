package com.nori.tc.messaging.domain.kafka;

import java.util.Set;

/**
 * Nori-TC Kafka 표준 토픽 상수 모음입니다.
 *
 * <p>목적:
 * 1) 각 모듈의 문자열 하드코딩을 제거하고,
 * 2) 토픽 분류(MES/비-MES) 로직을 공통화하며,
 * 3) 키 선택/검증 정책의 입력값 기준을 단일화하기 위함입니다.</p>
 */
public final class TcKafkaTopics {

    /**
     * Gateway -> Business 설비 이벤트 토픽입니다.
     */
    public static final String EQP_EVENTS = "tc.eqp.events";

    /**
     * Business -> Gateway 설비 명령 토픽입니다.
     */
    public static final String EQP_COMMANDS = "tc.eqp.commands";

    /**
     * Business -> 외부 MES 어댑터 명령 토픽입니다.
     */
    public static final String MES_COMMANDS = "tc.mes.commands";

    /**
     * 외부 MES 어댑터 -> Business 이벤트 토픽입니다.
     */
    public static final String MES_EVENTS = "tc.mes.events";

    /**
     * UI Backend -> Gateway/Business 이벤트 토픽입니다.
     */
    public static final String UI_EVENTS = "tc.ui.events";

    /**
     * Gateway/Business -> UI Backend 명령 토픽입니다.
     */
    public static final String UI_COMMANDS = "tc.ui.commands";

    /**
     * 시스템에서 사용하는 전체 토픽 집합입니다.
     */
    private static final Set<String> ALL_TOPICS = Set.of(
            EQP_EVENTS,
            EQP_COMMANDS,
            MES_COMMANDS,
            MES_EVENTS,
            UI_EVENTS,
            UI_COMMANDS
    );

    /**
     * MES 왕복 경계에 해당하는 토픽 집합입니다.
     */
    private static final Set<String> MES_TOPICS = Set.of(MES_EVENTS, MES_COMMANDS);

    /**
     * 유틸리티 클래스 인스턴스화를 방지합니다.
     */
    private TcKafkaTopics() {
    }

    /**
     * 전달된 토픽이 시스템 표준 토픽인지 확인합니다.
     *
     * @param topic 검사 대상 토픽명
     * @return true면 표준 토픽, false면 미등록 토픽
     */
    public static boolean isSupported(final String topic) {
        final String normalized = normalize(topic);
        if (normalized == null) {
            return false;
        }
        return ALL_TOPICS.contains(normalized);
    }

    /**
     * 전달된 토픽이 MES 경계 토픽인지 확인합니다.
     *
     * <p>MES 토픽은 key를 {@code correlationId}로 고정해야 하므로
     * 별도 분류 유틸리티를 제공합니다.</p>
     *
     * @param topic 검사 대상 토픽명
     * @return true면 MES 토픽, false면 비-MES 토픽
     */
    public static boolean isMesTopic(final String topic) {
        final String normalized = normalize(topic);
        if (normalized == null) {
            return false;
        }
        return MES_TOPICS.contains(normalized);
    }

    /**
     * 표준 토픽 목록을 읽기 전용 집합으로 반환합니다.
     *
     * @return 표준 토픽 불변 집합
     */
    public static Set<String> allTopics() {
        return ALL_TOPICS;
    }

    /**
     * 토픽 문자열을 정규화합니다.
     *
     * @param topic 원본 토픽 문자열
     * @return 공백 제거된 토픽 문자열, 값이 없으면 null
     */
    private static String normalize(final String topic) {
        if (topic == null) {
            return null;
        }
        final String normalized = topic.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}


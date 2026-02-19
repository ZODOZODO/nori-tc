package com.nori.tc.messaging.domain.kafka;

import java.util.Set;

/**
 * Kafka 메시지 metadata.source 표준 상수 모음입니다.
 *
 * <p>목적:
 * 1) source 문자열 오타를 줄이고,
 * 2) 소스 허용 목록(allowlist) 정책을 코드에서 일관되게 구성하며,
 * 3) 로깅/모니터링에서 발행 주체를 동일한 키로 집계하기 위함입니다.</p>
 */
public final class TcKafkaSources {

    /**
     * Gateway 애플리케이션 source 표준값입니다.
     */
    public static final String COMM_GATEWAY = "TC-COMM-GATEWAY";

    /**
     * Business 애플리케이션 source 표준값입니다.
     */
    public static final String BUSINESS_CORE = "TC-BUSINESS-CORE";

    /**
     * UI Backend 애플리케이션 source 표준값입니다.
     */
    public static final String UI_BACKEND = "TC-UI-BACKEND";

    /**
     * 외부 MES 어댑터 source 표준값입니다.
     */
    public static final String EXTERNAL_MES_ADAPTER = "TC-EXTERNAL-MES-ADAPTER";

    /**
     * 시스템에서 기본적으로 허용하는 source 집합입니다.
     */
    private static final Set<String> SUPPORTED_SOURCES = Set.of(
            COMM_GATEWAY,
            BUSINESS_CORE,
            UI_BACKEND,
            EXTERNAL_MES_ADAPTER
    );

    /**
     * 유틸리티 클래스 인스턴스화를 방지합니다.
     */
    private TcKafkaSources() {
    }

    /**
     * 전달된 source가 표준 source 집합에 포함되는지 확인합니다.
     *
     * @param source 검사 대상 source 문자열
     * @return true면 표준 source, false면 미등록 source
     */
    public static boolean isSupported(final String source) {
        if (source == null) {
            return false;
        }
        return SUPPORTED_SOURCES.contains(source.trim());
    }

    /**
     * 표준 source 목록을 읽기 전용 형태로 반환합니다.
     *
     * @return 표준 source 불변 집합
     */
    public static Set<String> supportedSources() {
        return SUPPORTED_SOURCES;
    }
}


package com.nori.tc.business.adapters.kafka.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * UI Kafka Listener 운영 정책 상태를 기동 시점에 출력하는 로거입니다.
 *
 * <p>목적:
 * 1) UI Listener 활성/비활성 상태를 운영자가 즉시 확인
 * 2) 비로컬 프로파일에서 비활성 상태일 때 경고 출력</p>
 */
@Component
public class BusinessUiListenerPolicyLogger {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiListenerPolicyLogger.class);
    private static final Set<String> LOCAL_PROFILES = Set.of("local", "dev", "test");

    private final Environment environment;

    @Value("${tc.business.core.ui-task.kafka-listener-enabled}")
    private boolean uiKafkaListenerEnabled;

    /**
     * 환경 정보 의존성을 주입받습니다.
     *
     * @param environment Spring 환경 정보
     */
    public BusinessUiListenerPolicyLogger(final Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment is null");
    }

    /**
     * 애플리케이션 기동 시 UI Listener 정책 상태를 로깅합니다.
     */
    @PostConstruct
    public void logPolicy() {
        final String[] activeProfiles = environment.getActiveProfiles();
        final boolean nonLocalProfile = Arrays.stream(activeProfiles)
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .noneMatch(LOCAL_PROFILES::contains);

        if (uiKafkaListenerEnabled) {
            log.info("UI Kafka listener policy: enabled=true, activeProfiles={}", Arrays.toString(activeProfiles));
            return;
        }

        if (nonLocalProfile) {
            log.warn("UI Kafka listener policy: enabled=false in non-local profile. activeProfiles={}, property=tc.business.core.ui-task.kafka-listener-enabled",
                    Arrays.toString(activeProfiles));
            return;
        }

        log.info("UI Kafka listener policy: enabled=false (local/dev/test profile). activeProfiles={}",
                Arrays.toString(activeProfiles));
    }
}

/*
 * tc-messaging-rendezvous-starter
 *
 * 역할
 * - Tibco Rendezvous(RV) 기반 메시징 어댑터를 앱에 자동 조립(AutoConfiguration)합니다.
 * - tc-messaging-rendezvous 어댑터를 Tibco RV 라이브러리와 연결합니다.
 * - 앱은 이 스타터 하나만 의존하면 Rendezvous MessagePublisherPort 빈을 주입받을 수 있습니다.
 *
 * 의존성 구조
 *   apps/xxx-app
 *     └── tc-messaging-rendezvous-starter (이 모듈)
 *           └── tc-messaging-rendezvous (어댑터: Tibco RV 구현체)
 *                 └── tc-messaging-core (포트 인터페이스: 기술 중립)
 *
 * 주의
 * - tibrv(tibrvj.jar)는 Maven Central에 없는 상용 라이브러리로, 로컬 파일 시스템에 배치 후 사용합니다.
 *   예: implementation(files("${rootDir}/libs/vendor/tibrvj.jar"))
 * - 이 스타터는 Kafka, RabbitMQ 와 무관합니다.
 *   메시징 기술별 스타터는 정확히 1개만 앱에 포함해야 합니다.
 */

plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * Tibco RV 메시징 어댑터입니다.
     * - MessagePublisherPort 의 Tibco Rendezvous 구현체를 포함합니다.
     */
    implementation(project(":libs:messaging:adapter:tc-messaging-rendezvous"))

    /*
     * Spring Boot 기본 AutoConfiguration 지원 의존성입니다.
     * - @AutoConfiguration, @ConditionalOnClass 등 조건부 빈 등록에 필요합니다.
     */
    implementation(libs.spring.boot.starter)

    /*
     * Tibco Rendezvous(RV) 클라이언트 라이브러리입니다.
     * - Maven Central 미제공 상용 라이브러리이므로 로컬 또는 사내 저장소에서 해결합니다.
     * - tc.messaging.rendezvous.* 프로퍼티로 데몬/서비스/네트워크 정보를 설정합니다.
     */
    implementation(libs.tibrv)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

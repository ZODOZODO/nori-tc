/*
 * tc-messaging-rendezvous (Adapter)
 *
 * 역할
 * - MessagePublisherPort 의 Tibco Rendezvous(RV) 구현체를 제공합니다.
 * - 기술 중립 포트(tc-messaging-core)를 Tibco RV API(tibrvj)로 구현합니다.
 * - tibrv 라이브러리는 compileOnly 로 선언하여 런타임 제공은 스타터에 위임합니다.
 *
 * 구현 예정 구성
 *   - RendezvousMessagePublisher : MessagePublisherPort 구현체
 *   - TcRendezvousProperties     : @ConfigurationProperties (tc.messaging.rendezvous.*)
 *
 * 주의
 * - tibrvj.jar 는 상용 라이브러리로, 로컬 파일 시스템 또는 사내 저장소에서 해결해야 합니다.
 *   빌드 실패 시 libs/vendor/tibrvj.jar 경로를 확인하거나 사내 Nexus 설정을 점검하세요.
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
     * 기술 중립 포트 인터페이스(MessagePublisherPort, MessagePublishRequest)를 노출합니다.
     * - api 로 선언하여 이 어댑터를 의존하는 스타터/앱이 포트 타입을 함께 참조할 수 있습니다.
     */
    api(project(":libs:messaging:tc-messaging-core"))

    /*
     * Tibco RV 클라이언트 라이브러리입니다.
     * - compileOnly 로 선언하여 런타임 클래스패스는 스타터(tc-messaging-rendezvous-starter)에서 제공합니다.
     */
    compileOnly(libs.tibrv)

    /*
     * @ConfigurationProperties, @Component 등 Spring 어노테이션 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}

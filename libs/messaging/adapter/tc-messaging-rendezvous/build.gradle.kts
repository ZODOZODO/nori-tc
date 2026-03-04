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

/**
 * Tibco RV 상용 JAR의 로컬 경로입니다.
 *
 * - Maven Central에는 com.tibco:tibrvj 좌표가 존재하지 않으므로,
 *   개발 환경에서는 로컬 파일 기반 의존성으로 처리합니다.
 * - IDE Gradle sync 단계에서 미해결 좌표 오류를 제거하기 위해
 *   파일 존재 여부를 확인한 뒤 조건부로 의존성을 추가합니다.
 */
val localTibrvJar = rootProject.layout.projectDirectory.file("libs/vendor/tibrvj.jar").asFile

dependencies {
    /*
     * 기술 중립 포트 인터페이스(MessagePublisherPort, MessagePublishRequest)를 노출합니다.
     * - api 로 선언하여 이 어댑터를 의존하는 스타터/앱이 포트 타입을 함께 참조할 수 있습니다.
     */
    api(project(":libs:messaging:tc-messaging-core"))

    /*
     * Tibco RV 클라이언트 라이브러리입니다.
     *
     * - 상용 라이브러리 특성상 Maven 좌표 해석 대신 로컬 파일을 직접 사용합니다.
     * - adapter 모듈은 컴파일 시점에만 RV API 타입이 필요하므로 compileOnly로 선언합니다.
     */
    if (localTibrvJar.exists()) {
        compileOnly(files(localTibrvJar))
    } else {
        logger.lifecycle(
            "[tc-messaging-rendezvous] libs/vendor/tibrvj.jar 파일이 없어 Tibco RV 의존성을 건너뜁니다. " +
                "Rendezvous 구현 코드를 추가할 경우 해당 파일을 배치해야 컴파일할 수 있습니다."
        )
    }

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

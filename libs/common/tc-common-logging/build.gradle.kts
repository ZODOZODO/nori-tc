/*
 * tc-common-logging
 *
 * 역할
 * - 모든 앱/라이브러리에서 재사용하는 공통 로깅 유틸리티(MDC/필터/압축)를 제공합니다.
 * - Spring Boot AutoConfiguration 및 logback-spring.xml 을 함께 제공하여
 *   애플리케이션 경계에서 동일한 로그 정책을 적용할 수 있게 합니다.
 *
 * 설계 원칙
 * - 기존 로그 스타터 모듈을 제거하고 단일 공통 모듈로 통합합니다.
 * - core/adapter 계층은 starter에 의존하지 않고 이 모듈만 참조합니다.
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
     * Spring Boot 자동 설정 타입(@AutoConfiguration, @EnableConfigurationProperties) 컴파일 의존성입니다.
     * - 런타임 제공 책임은 상위 앱의 spring-boot-starter에 있습니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.boot.autoconfigure)

    /*
     * 로깅 유틸리티(TcLogContext, TcMdcTaskDecorator)가 MDC API를 직접 사용하므로
     * 컴파일 시점에 SLF4J API가 필요합니다.
     */
    compileOnly(libs.slf4j.api)

    /*
     * 로그 필터(AppMdcAbsenceFilter, EqpMdcPresenceFilter)는 Logback 클래스를 직접 확장하므로
     * 컴파일 시점에 logback-classic 타입이 필요합니다.
     * - 버전은 루트 BOM(spring-boot-dependencies)에서 관리합니다.
     */
    compileOnly("ch.qos.logback:logback-classic")

    /*
     * @PostConstruct, @PreDestroy 등 Jakarta 어노테이션 컴파일 의존성입니다.
     */
    compileOnly(libs.jakarta.annotation.api)
}

tasks.test {
    useJUnitPlatform()
}

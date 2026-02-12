/*
 * tc-comm-domain (FIX)
 *
 * 역할
 * - 통합 tc-comm-gateway 전반에서 공통으로 쓰는 "도메인/계약"만 제공하는 매우 작은 모듈입니다.
 * - 공통 타입(enum), 제한값, 안전한 유틸(base64/ulid), DLQ 표준 모델 등을 포함합니다.
 *
 * 금지
 * - Netty, Spring, Kafka, DB(JPA/MyBatis/JDBC) 같은 프레임워크/외부시스템 의존성 추가 금지
 * - 게이트웨이 실행 흐름(usecase), 라우팅 엔진 등 “핵심 로직”은 tc-comm-core로 이동
 */

plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // 루트 build.gradle.kts에서 toolchain(Java 21)이 적용되지만,
    // 모듈 단독 실행/분리 시에도 명확히 하기 위해 명시합니다.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // 의도적으로 비워둡니다.
    // domain은 어떤 persistence 기술에도 의존하지 않는 순수 계층이어야 합니다.
}

tasks.test {
    useJUnitPlatform()
}

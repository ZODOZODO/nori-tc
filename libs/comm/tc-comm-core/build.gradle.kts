/*
 * tc-db-domain (FIX)
 *
 * tc-comm-core (Gateway Core Engine)
 *
 * 역할
 * - 통합 tc-comm-gateway의 핵심 실행 엔진(순차 처리, reassembly, 라우팅, usecase, port)을 제공
 * - 기술 구현(Netty/Kafka/DB)은 여기서 금지하고 Port(interface)로만 의존
 *
 * 의존성
 * - tc-comm-domain(Shared Kernel): 공통 타입/유틸/제한/DLQ 표준 모델 제공
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
    // core는 domain(shared kernel)을 API로 노출하는 편이 사용성이 좋습니다.
    // (hsms/socket/app이 core만 의존해도 domain 타입이 함께 보이도록)
    api(project(":libs:comm:tc-comm-domain"))

    // 외부 의존성(프레임워크/클라이언트 라이브러리) 금지
}

tasks.test {
    useJUnitPlatform()
}

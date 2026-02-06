/*
 * tc-comm-hsms
 * - 통합 tc-comm-gateway에서 HSMS 전용 파이프라인(프레임 추출 + 세션 제어 + SECS-II 디코딩)을 제공하는 라이브러리 모듈
 *
 * 설계 원칙
 * - Netty/Kafka/DB 같은 기술 의존성은 절대 포함하지 않습니다.
 * - tc-comm-core의 Port/모델에 의존하여 "순수 로직"만 제공합니다.
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
    // HSMS는 core 엔진(EquipmentRuntimeContext, InboundPipelinePort, ParsedMessage 등)을 구현/사용합니다.
    api(project(":libs:comm:tc-comm-core"))
}

tasks.test {
    useJUnitPlatform()
}

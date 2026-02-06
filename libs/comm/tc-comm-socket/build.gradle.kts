/*
 * tc-comm-socket
 *
 * - 통합 tc-comm-gateway에서 SOCKET 전용 파이프라인(프레임 추출 + socketType별 파싱/인코딩)을 제공하는 라이브러리 모듈
 *
 * 설계 원칙
 * - Netty/Kafka/DB 같은 기술 의존성은 포함하지 않습니다.
 * - tc-comm-core의 InboundPipelinePort(Port) 구현으로 연결됩니다.
 * - socketType별로 디렉터리를 분리하여 유지보수성과 가독성을 확보합니다.
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
    api(project(":libs:comm:tc-comm-core"))
}

tasks.test {
    useJUnitPlatform()
}

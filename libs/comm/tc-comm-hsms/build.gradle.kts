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

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
    // Gateway 플러그인 SDK: SocketTypeHandler/Frame/DecodeResult/EncodeResult SPI 계약
    api(project(":libs:action:tc-gateway-action"))
    // tc-comm-core는 tc-gateway-action이 api로 전이하므로 명시적으로 중복 선언하지 않아도 됩니다.
    // 단, 직접 의존이 명시적으로 필요한 경우를 위해 유지합니다.
    api(project(":libs:comm:tc-comm-core"))
}

tasks.test {
    useJUnitPlatform()
}

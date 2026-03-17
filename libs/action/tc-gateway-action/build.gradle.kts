plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // Gateway 플러그인 JAR 개발자를 위한 경량 SDK 모듈입니다.
    // 순수 Java이므로 Spring/Kafka 의존성이 없습니다.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // ReassemblyBuffer(SocketTypeHandler.tryExtractOne 파라미터)를 위해 필요합니다.
    // tc-comm-core는 순수 Java이므로 플러그인 JAR 개발자가 compileOnly로 사용해도 안전합니다.
    api(project(":libs:comm:tc-comm-core"))
}

tasks.test {
    useJUnitPlatform()
}

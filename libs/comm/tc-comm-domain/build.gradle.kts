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

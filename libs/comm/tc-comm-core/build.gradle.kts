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

plugins {
    `java-library`
    // BOM 적용을 위해 사용(루트 build.gradle.kts에서 이 플러그인 적용 모듈에 BOM import)
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
     * core
     * - JPA 어댑터(Store 구현체)가 구현해야 하는 Port(Store 인터페이스)가 들어있다.
     */
    implementation(project(":libs:db:tc-db-core"))

    /*
     * domain (명시적 의존성 권장)
     * - Entity에서 enum/DTO를 직접 import할 수 있다.
     * - core가 domain을 api로 노출하더라도, 여기서 명시하면 의도가 더 분명해진다.
     */
    implementation(project(":libs:db:tc-db-domain"))

    // JPA + Hibernate + Spring Data JPA
    implementation(libs.spring.boot.starter.data.jpa)

    /*
     * MapStruct (FIX: 유지보수성 개선)
     * - Entity <-> Domain 객체 매핑 코드를 컴파일 타임에 자동 생성한다.
     * - implementation: 런타임에 필요한 어노테이션(@Mapper, @Mapping 등)
     * - annotationProcessor: 컴파일 타임 코드 생성기 (*MapperImpl.java 생성)
     */
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // ✅ 하드코딩 제거 → catalog alias 사용
    testImplementation(libs.spring.boot.starter.test)
    /*
     * U16 저장소 단위 테스트 실행 시 Gradle Test Executor가 JUnit Platform launcher를 요구할 수 있으므로
     * 런타임 클래스패스에 명시적으로 추가합니다.
     */
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

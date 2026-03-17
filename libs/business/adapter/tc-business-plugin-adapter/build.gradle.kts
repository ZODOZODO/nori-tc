plugins {
    `java-library`
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
     * Plugin 어댑터는 코어 포트를 구현하므로 코어 API를 의존합니다.
     */
    api(project(":libs:business:tc-business-core"))

    /*
     * AbstractXxxActionExecutor / @TcAction / TcActionContext 타입을 직접 참조하므로
     * transitive 의존에 의존하지 않고 명시적으로 선언합니다.
     */
    implementation(project(":libs:action:tc-business-action"))

    /*
     * 플러그인 preload/reload 시 DB 저장소를 조회합니다.
     */
    implementation(project(":libs:db:tc-db-core"))
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * Spring 컴포넌트/프로퍼티 바인딩 어노테이션 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * Plugin 어댑터 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

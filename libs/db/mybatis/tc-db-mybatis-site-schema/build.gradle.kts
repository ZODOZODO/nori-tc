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
     * site schema에서도 domain 타입(enum 등)을 Mapper/DTO에서 사용할 수 있도록 유지
     */
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * site schema에서도 @Param 등 MyBatis 타입이 컴파일에 필요할 수 있다.
     * - ✅ libs.mybatis.core로 통일
     */
    compileOnly(libs.mybatis.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mybatis.core)
}

tasks.test {
    useJUnitPlatform()
}

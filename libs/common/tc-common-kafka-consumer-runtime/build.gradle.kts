plugins {
    `java-library`
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
     * Public API uses TopicPartition/OffsetAndMetadata.
     * Keep kafka-clients as API so downstream modules can use the exposed types.
     */
    api(libs.kafka.clients)
    api(libs.slf4j.api)

    /*
     * Unit test dependencies.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

/*
 * tc-comm-gateway-app
 *
 * Intent
 * - Assembly module only. No app-specific DB/Kafka/Redis implementations.
 * - Enable exactly one relational DB starter at a time.
 *
 * Notes
 * - Spring Boot plugin + starter are required for compile/runtime.
 * - Multiple DB starters will conflict on bean names and fail fast.
 */

plugins {
    // Spring Boot packaging/runtime plugin
    alias(libs.plugins.spring.boot)

    // BOM/version alignment plugin
    alias(libs.plugins.spring.dependency.management)

    // Java compiler plugin
    java
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // Java 21 toolchain
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * =========================
     * Spring Boot baseline
     * =========================
     *
     * - Required to compile SpringApplication/@SpringBootApplication.
     * - Does not imply a web server.
     */
    implementation(libs.spring.boot.starter)

    /*
     * =========================
     * DB Starter (pick exactly one)
     * =========================
     *
     * - Choose DB technology (JPA/MyBatis) and vendor (Postgres/MySQL/etc).
     * - To switch, comment out the current one and enable another.
     */

    // DEFAULT: PostgreSQL + JPA
    implementation(project(":libs:db:starter:tc-db-postgres-jpa-starter"))

    // PostgreSQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-postgres-mybatis-starter"))

    // MySQL + JPA
    // implementation(project(":libs:db:starter:tc-db-mysql-jpa-starter"))

    // MySQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-mysql-mybatis-starter"))

    // MSSQL + JPA
    // implementation(project(":libs:db:starter:tc-db-mssql-jpa-starter"))

    // MSSQL + MyBatis
    // implementation(project(":libs:db:starter:tc-db-mssql-mybatis-starter"))

    // Oracle + JPA
    // implementation(project(":libs:db:starter:tc-db-oracle-jpa-starter"))

    // Oracle + MyBatis
    // implementation(project(":libs:db:starter:tc-db-oracle-mybatis-starter"))

    // Redis (cache/runtime state) uses the shared DB Redis starter.
    implementation(project(":libs:db:starter:tc-db-redis-starter"))

    /*
     * =========================
     * Messaging (Kafka)
     * =========================
     */
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))
    // App-level Kafka classes (KafkaListener/KafkaTemplate) require compile deps.
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)

    /*
     * =========================
     * Communication Core (HSMS/SOCKET)
     * =========================
     */
    implementation(project(":libs:comm:tc-comm-core"))
    implementation(project(":libs:comm:tc-comm-hsms"))
    implementation(project(":libs:comm:tc-comm-socket"))

    /*
     * =========================
     * Test
     * =========================
     */
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

/*
 * main class
 * - Explicitly set Spring Boot main class for packaging.
 */
springBoot {
    mainClass.set("com.nori.tc.apps.commgateway.TcCommGatewayApplication")
}

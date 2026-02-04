/*
 * settings.gradle (Project Configuration)
 *
 * [핵심 설정 메모]
 * 1. Version Catalog: "libs"는 gradle/libs.versions.toml 파일을 Gradle이 자동으로 로딩합니다.
 * 따라서 별도의 versionCatalogs { create("libs") ... } 설정이 필요 없습니다.
 * 2. Hexagonal Architecture:
 * - Domain: 순수 비즈니스 객체 (POJO)
 * - Core: 기술 중립적인 인터페이스 (Port)
 * - Adapter: 특정 기술(JPA, Kafka 등) 구현체
 * - Starter: App이 의존성을 쉽게 추가하기 위한 AutoConfiguration 모음
 */

rootProject.name = "nori-tc"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

/* ===================================================
 * 1. Apps (Applications)
 * - 실제 실행되는 서비스 애플리케이션
 * =================================================== */
include(":apps:tc-comm-gateway-app")
project(":apps:tc-comm-gateway-app").projectDir = file("apps/tc-comm-gateway-app")


/* ===================================================
 * 2. DB Modules (Persistence Layer)
 * - Hexagonal Architecture applied
 * =================================================== */

// [Domain] 순수 도메인 객체 (Entity와 분리)
include(":libs:db:tc-db-domain")
project(":libs:db:tc-db-domain").projectDir = file("libs/db/tc-db-domain")

// [Core/Port] 저장소 인터페이스 (Repository Interface)
include(":libs:db:tc-db-core")
project(":libs:db:tc-db-core").projectDir = file("libs/db/tc-db-core")

// [Adapter/JPA] JPA 엔티티 및 구현체
include(":libs:db:jpa:tc-db-jpa-common-schema")
project(":libs:db:jpa:tc-db-jpa-common-schema").projectDir = file("libs/db/jpa/tc-db-jpa-common-schema")

include(":libs:db:jpa:tc-db-jpa-site-schema")
project(":libs:db:jpa:tc-db-jpa-site-schema").projectDir = file("libs/db/jpa/tc-db-jpa-site-schema")

// [Adapter/MyBatis] MyBatis XML 및 Mapper 구현체
include(":libs:db:mybatis:tc-db-mybatis-common-schema")
project(":libs:db:mybatis:tc-db-mybatis-common-schema").projectDir = file("libs/db/mybatis/tc-db-mybatis-common-schema")

include(":libs:db:mybatis:tc-db-mybatis-site-schema")
project(":libs:db:mybatis:tc-db-mybatis-site-schema").projectDir = file("libs/db/mybatis/tc-db-mybatis-site-schema")

// [Starters] DB 기술 + 벤더 조합별 자동 설정 (8 combos)
include(":libs:db:starter:tc-db-postgres-jpa-starter")
project(":libs:db:starter:tc-db-postgres-jpa-starter").projectDir = file("libs/db/starter/tc-db-postgres-jpa-starter")

include(":libs:db:starter:tc-db-postgres-mybatis-starter")
project(":libs:db:starter:tc-db-postgres-mybatis-starter").projectDir = file("libs/db/starter/tc-db-postgres-mybatis-starter")

include(":libs:db:starter:tc-db-mysql-jpa-starter")
project(":libs:db:starter:tc-db-mysql-jpa-starter").projectDir = file("libs/db/starter/tc-db-mysql-jpa-starter")

include(":libs:db:starter:tc-db-mysql-mybatis-starter")
project(":libs:db:starter:tc-db-mysql-mybatis-starter").projectDir = file("libs/db/starter/tc-db-mysql-mybatis-starter")

include(":libs:db:starter:tc-db-mssql-jpa-starter")
project(":libs:db:starter:tc-db-mssql-jpa-starter").projectDir = file("libs/db/starter/tc-db-mssql-jpa-starter")

include(":libs:db:starter:tc-db-mssql-mybatis-starter")
project(":libs:db:starter:tc-db-mssql-mybatis-starter").projectDir = file("libs/db/starter/tc-db-mssql-mybatis-starter")

include(":libs:db:starter:tc-db-oracle-jpa-starter")
project(":libs:db:starter:tc-db-oracle-jpa-starter").projectDir = file("libs/db/starter/tc-db-oracle-jpa-starter")

include(":libs:db:starter:tc-db-oracle-mybatis-starter")
project(":libs:db:starter:tc-db-oracle-mybatis-starter").projectDir = file("libs/db/starter/tc-db-oracle-mybatis-starter")


/* ===================================================
 * 3. Messaging Modules (Middleware Layer)
 * - Kafka, RabbitMQ, TibcoRV 통합 모듈
 * =================================================== */

// [Domain] 메시지 이벤트 객체 (POJO, 기술 중립)
include(":libs:messaging:tc-messaging-domain")
project(":libs:messaging:tc-messaging-domain").projectDir = file("libs/messaging/tc-messaging-domain")

// [Core/Port] Publisher/Subscriber 인터페이스 (기술 중립)
include(":libs:messaging:tc-messaging-core")
project(":libs:messaging:tc-messaging-core").projectDir = file("libs/messaging/tc-messaging-core")

// [Adapter] 실제 메시징 기술 구현체 (Implementation)
include(":libs:messaging:adapter:tc-messaging-kafka")
project(":libs:messaging:adapter:tc-messaging-kafka").projectDir = file("libs/messaging/adapter/tc-messaging-kafka")

include(":libs:messaging:adapter:tc-messaging-rabbitmq")
project(":libs:messaging:adapter:tc-messaging-rabbitmq").projectDir = file("libs/messaging/adapter/tc-messaging-rabbitmq")

include(":libs:messaging:adapter:tc-messaging-rendezvous")
project(":libs:messaging:adapter:tc-messaging-rendezvous").projectDir = file("libs/messaging/adapter/tc-messaging-rendezvous")

// [Starters] App 연동을 위한 자동 설정 (AutoConfiguration)
include(":libs:messaging:starter:tc-messaging-kafka-starter")
project(":libs:messaging:starter:tc-messaging-kafka-starter").projectDir = file("libs/messaging/starter/tc-messaging-kafka-starter")

include(":libs:messaging:starter:tc-messaging-rabbitmq-starter")
project(":libs:messaging:starter:tc-messaging-rabbitmq-starter").projectDir = file("libs/messaging/starter/tc-messaging-rabbitmq-starter")

include(":libs:messaging:starter:tc-messaging-rendezvous-starter")
project(":libs:messaging:starter:tc-messaging-rendezvous-starter").projectDir = file("libs/messaging/starter/tc-messaging-rendezvous-starter")

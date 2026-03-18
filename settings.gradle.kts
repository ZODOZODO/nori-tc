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

/* ===================================================
 * Composite Build: 분리된 Action SDK 레포를 로컬 소스로 참조합니다.
 * - Maven 좌표(com.nori.tc:nori-tc-gateway-action 등)를 사용하는 의존성을
 *   로컬 레포 소스로 자동 대체합니다.
 * - 내부 Maven 저장소 구축 후에는 이 블록을 제거하고 Maven 좌표만 유지합니다.
 * =================================================== */
includeBuild("../nori-tc-gateway-action")
includeBuild("../nori-tc-business-action")

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

include(":apps:tc-business-core-app")
project(":apps:tc-business-core-app").projectDir = file("apps/tc-business-core-app")

include(":apps:tc-ui-backend-app")
project(":apps:tc-ui-backend-app").projectDir = file("apps/tc-ui-backend-app")


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

include(":libs:db:starter:tc-db-redis-starter")
project(":libs:db:starter:tc-db-redis-starter").projectDir = file("libs/db/starter/tc-db-redis-starter")

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

// [Kafka Shared] Kafka 전용 공통 계약/런타임 (Business/Comm Kafka Adapter 재사용)
include(":libs:messaging:kafka:tc-messaging-kafka-contract")
project(":libs:messaging:kafka:tc-messaging-kafka-contract").projectDir = file("libs/messaging/kafka/tc-messaging-kafka-contract")

include(":libs:messaging:kafka:tc-messaging-kafka-runtime")
project(":libs:messaging:kafka:tc-messaging-kafka-runtime").projectDir = file("libs/messaging/kafka/tc-messaging-kafka-runtime")

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

/* ===================================================
 * 4. Action SDK Modules (별도 레포로 분리됨)
 * - nori-tc-gateway-action / nori-tc-business-action 레포 참조
 * - Gradle Composite Build로 로컬 소스를 Maven 좌표처럼 참조합니다.
 * - 내부 Maven 저장소 준비 후 includeBuild를 Maven 좌표 참조로 전환합니다.
 * =================================================== */

/* ===================================================
 * 5. Gateway Modules (comm-gateway Layer)
 * - HSMS, Socket 모듈
 * =================================================== */

include(":libs:comm")
project(":libs:comm").projectDir = file("libs/comm")

include(":libs:comm:tc-comm-domain")
project(":libs:comm:tc-comm-domain").projectDir = file("libs/comm/tc-comm-domain")

include(":libs:comm:tc-comm-core")
project(":libs:comm:tc-comm-core").projectDir = file("libs/comm/tc-comm-core")

include(":libs:comm:tc-comm-hsms")
project(":libs:comm:tc-comm-hsms").projectDir = file("libs/comm/tc-comm-hsms")

include(":libs:comm:tc-comm-socket")
project(":libs:comm:tc-comm-socket").projectDir = file("libs/comm/tc-comm-socket")

/* ===================================================
 * 5. Comm Gateway Modules (App Composition Layer)
 * - Core / Adapters / Starter
 * =================================================== */

include(":libs:comm:tc-comm-gateway-core")
project(":libs:comm:tc-comm-gateway-core").projectDir = file("libs/comm/tc-comm-gateway-core")

include(":libs:comm:adapter:tc-comm-gateway-netty-adapter")
project(":libs:comm:adapter:tc-comm-gateway-netty-adapter").projectDir = file("libs/comm/adapter/tc-comm-gateway-netty-adapter")

include(":libs:comm:adapter:tc-comm-gateway-kafka-adapter")
project(":libs:comm:adapter:tc-comm-gateway-kafka-adapter").projectDir = file("libs/comm/adapter/tc-comm-gateway-kafka-adapter")

include(":libs:comm:adapter:tc-comm-gateway-db-adapter")
project(":libs:comm:adapter:tc-comm-gateway-db-adapter").projectDir = file("libs/comm/adapter/tc-comm-gateway-db-adapter")

include(":libs:comm:adapter:tc-comm-gateway-redis-adapter")
project(":libs:comm:adapter:tc-comm-gateway-redis-adapter").projectDir = file("libs/comm/adapter/tc-comm-gateway-redis-adapter")

include(":libs:comm:adapter:tc-comm-gateway-plugin-adapter")
project(":libs:comm:adapter:tc-comm-gateway-plugin-adapter").projectDir = file("libs/comm/adapter/tc-comm-gateway-plugin-adapter")

include(":libs:comm:starter:tc-comm-gateway-starter")
project(":libs:comm:starter:tc-comm-gateway-starter").projectDir = file("libs/comm/starter/tc-comm-gateway-starter")

/* ===================================================
 * 6. Common Modules
 * - Cross-app reusable runtime/algorithm modules
 * =================================================== */

include(":libs:common:tc-common-mailbox")
project(":libs:common:tc-common-mailbox").projectDir = file("libs/common/tc-common-mailbox")

// [Neutral Consumer Runtime] Kafka 타입을 직접 노출하지 않는 중립 소비 런타임 공용 모듈
include(":libs:common:tc-common-consumer-runtime")
project(":libs:common:tc-common-consumer-runtime").projectDir = file("libs/common/tc-common-consumer-runtime")

include(":libs:common:tc-common-task-execution")
project(":libs:common:tc-common-task-execution").projectDir = file("libs/common/tc-common-task-execution")

// [Common Logging] 전역 MDC/로그 필터/압축 스케줄러/Logback 설정을 제공하는 공용 모듈
include(":libs:common:tc-common-logging")
project(":libs:common:tc-common-logging").projectDir = file("libs/common/tc-common-logging")

/* ===================================================
 * 7. Business Core Modules (App Composition Layer)
 * - Domain / Core / Adapters / Starter
 * =================================================== */

include(":libs:business:tc-business-domain")
project(":libs:business:tc-business-domain").projectDir = file("libs/business/tc-business-domain")

include(":libs:business:tc-business-core")
project(":libs:business:tc-business-core").projectDir = file("libs/business/tc-business-core")

include(":libs:business:adapter:tc-business-db-adapter")
project(":libs:business:adapter:tc-business-db-adapter").projectDir = file("libs/business/adapter/tc-business-db-adapter")

include(":libs:business:adapter:tc-business-kafka-adapter")
project(":libs:business:adapter:tc-business-kafka-adapter").projectDir = file("libs/business/adapter/tc-business-kafka-adapter")

include(":libs:business:adapter:tc-business-plugin-adapter")
project(":libs:business:adapter:tc-business-plugin-adapter").projectDir = file("libs/business/adapter/tc-business-plugin-adapter")

include(":libs:business:adapter:tc-business-redis-adapter")
project(":libs:business:adapter:tc-business-redis-adapter").projectDir = file("libs/business/adapter/tc-business-redis-adapter")

include(":libs:business:starter:tc-business-core-starter")
project(":libs:business:starter:tc-business-core-starter").projectDir = file("libs/business/starter/tc-business-core-starter")

/* ===================================================
 * 8. UI Backend Modules (App Composition Layer)
 * - UI Backend 전용 헥사고날 아키텍처 모듈
 * - Front ↔ UI-backend: REST API 전용 (WebSocket/SSE 없음)
 * - 인증: DB 세션 토큰 + Business Redis 캐시
 * - Gateway Redis(6379) + Business Redis(6380) 동시 접속
 * =================================================== */

// [Domain] UI 전용 순수 도메인 객체 (AuthToken, UserPrincipal, UiTaskResult)
include(":libs:ui:tc-ui-domain")
project(":libs:ui:tc-ui-domain").projectDir = file("libs/ui/tc-ui-domain")

// [Core/Port] UseCase + Port 인터페이스 (기술 중립)
include(":libs:ui:tc-ui-core")
project(":libs:ui:tc-ui-core").projectDir = file("libs/ui/tc-ui-core")

// [Adapter/JPA] DB 포트 구현체 (기존 common-schema Entity 재사용)
include(":libs:ui:adapter:tc-ui-db-adapter")
project(":libs:ui:adapter:tc-ui-db-adapter").projectDir = file("libs/ui/adapter/tc-ui-db-adapter")

// [Adapter/Kafka] tc.ui.events.gateway/business 발행 + tc.ui.commands 수신
include(":libs:ui:adapter:tc-ui-kafka-adapter")
project(":libs:ui:adapter:tc-ui-kafka-adapter").projectDir = file("libs/ui/adapter/tc-ui-kafka-adapter")

// [Adapter/Redis] Gateway/Business Redis 듀얼 접속 (DLQ, 토큰 캐시, async 결과)
include(":libs:ui:adapter:tc-ui-redis-adapter")
project(":libs:ui:adapter:tc-ui-redis-adapter").projectDir = file("libs/ui/adapter/tc-ui-redis-adapter")

// [Adapter/Web] Spring Security + REST 컨트롤러 (AuthController, EqpController 등)
include(":libs:ui:adapter:tc-ui-web-adapter")
project(":libs:ui:adapter:tc-ui-web-adapter").projectDir = file("libs/ui/adapter/tc-ui-web-adapter")

// [Starter] 어댑터 4종 + AutoConfiguration 조립 (apps/tc-ui-backend-app에서 단일 참조)
include(":libs:ui:starter:tc-ui-backend-starter")
project(":libs:ui:starter:tc-ui-backend-starter").projectDir = file("libs/ui/starter/tc-ui-backend-starter")

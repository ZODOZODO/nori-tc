/*
 * tc-ui-core
 *
 * 역할
 * - UI Backend의 Port 인터페이스, UseCase, Registry를 정의합니다.
 * - 기술 중립적인 계층으로, 특정 Kafka/JPA/Redis 구현에 의존하지 않습니다.
 *
 * 주요 구성 (Phase 2에서 구현)
 * Port (DB):
 *   - UserPort           : tc_user_info CRUD
 *   - SessionPort        : tc_ui_auth_session CRUD
 *   - PermissionPort     : tc_ui_permission + 그룹 권한 조회
 *   - TokenCachePort     : 토큰 캐싱 (business Redis)
 *   - AsyncResultStorePort: eqp_start/end 결과 임시 저장
 *   - UiGatewayEqpRoutePartitionLookupPort: tc_eqp.route_partition 조회 (U13)
 * Port (Kafka 발행):
 *   - UiGatewayEventPublishPort  : tc.ui.events.gateway
 *   - UiBusinessEventPublishPort : tc.ui.events.business
 * Port (Kafka 수신):
 *   - UiCommandIngressPort : tc.ui.commands 수신 처리
 * UseCase:
 *   - LoginUseCase, LogoutUseCase, ValidateTokenUseCase
 * Registry:
 *   - DualResponseRegistry : eqp_create/update/delete DeferredResult 관리
 */

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
     * 도메인 모델(AuthToken, UserPrincipal, UiTaskResult)을 Port 시그니처에서 사용합니다.
     */
    api(project(":libs:ui:tc-ui-domain"))

    /*
     * DB 도메인 레코드(TcUserInfo, TcUiAuthSession)를 Port 시그니처에서 사용합니다.
     * api로 선언하여 tc-ui-db-adapter가 동일 타입을 별도 의존성 없이 사용할 수 있도록 노출합니다.
     */
    api(project(":libs:db:tc-db-domain"))

    /*
     * Kafka 메시지 계약(KafkaUiTaskMessage, KafkaUiTaskReplyMessage)을 Port 시그니처에서 사용합니다.
     * api로 선언하여 tc-ui-kafka-adapter가 동일 타입을 사용할 수 있도록 노출합니다.
     */
    api(project(":libs:messaging:kafka:tc-messaging-kafka-contract"))

    /*
     * Spring 생명주기/설정 바인딩 컴파일 의존성
     * - @Component, @Service 등 어노테이션은 컴파일 시에만 필요합니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

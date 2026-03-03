/*
 * tc-messaging-rabbitmq-starter
 *
 * 역할
 * - RabbitMQ 기반 메시징 어댑터를 앱에 자동 조립(AutoConfiguration)합니다.
 * - tc-messaging-rabbitmq 어댑터를 Spring Boot AMQP 인프라와 연결합니다.
 * - 앱은 이 스타터 하나만 의존하면 RabbitMQ MessagePublisherPort 빈을 주입받을 수 있습니다.
 *
 * 의존성 구조
 *   apps/xxx-app
 *     └── tc-messaging-rabbitmq-starter (이 모듈)
 *           └── tc-messaging-rabbitmq (어댑터: RabbitMQ 구현체)
 *                 └── tc-messaging-core (포트 인터페이스: 기술 중립)
 *
 * 주의
 * - spring-boot-starter-amqp 가 RabbitMQ ConnectionFactory, RabbitTemplate 빈을 자동 구성합니다.
 * - spring.rabbitmq.* 프로퍼티로 연결 정보를 제어합니다.
 * - 이 스타터는 Kafka 와 무관합니다. Kafka 가 필요하면 tc-messaging-kafka-starter 를 사용하세요.
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
     * RabbitMQ 메시징 어댑터입니다.
     * - MessagePublisherPort 의 RabbitMQ 구현체를 포함합니다.
     */
    implementation(project(":libs:messaging:adapter:tc-messaging-rabbitmq"))

    /*
     * Spring Boot 기본 AutoConfiguration 지원 의존성입니다.
     * - @AutoConfiguration, @ConditionalOnClass 등 조건부 빈 등록에 필요합니다.
     */
    implementation(libs.spring.boot.starter)

    /*
     * Spring AMQP (RabbitMQ) 스타터입니다.
     * - RabbitTemplate, ConnectionFactory, SimpleMessageListenerContainer 빈을 자동 구성합니다.
     * - spring.rabbitmq.* 프로퍼티로 브로커 주소, 포트, 자격증명을 설정합니다.
     */
    implementation(libs.spring.boot.starter.amqp)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}

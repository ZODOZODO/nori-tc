/*
 * tc-messaging-rabbitmq (Adapter)
 *
 * 역할
 * - MessagePublisherPort 의 RabbitMQ 구현체를 제공합니다.
 * - 기술 중립 포트(tc-messaging-core)를 RabbitTemplate 기반으로 구현합니다.
 * - Spring AMQP API는 compileOnly 로 선언하여 런타임 제공은 스타터에 위임합니다.
 *
 * 구현 예정 구성
 *   - RabbitMQMessagePublisher : MessagePublisherPort 구현체
 *   - TcRabbitProperties       : @ConfigurationProperties (tc.messaging.rabbitmq.*)
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
     * 기술 중립 포트 인터페이스(MessagePublisherPort, MessagePublishRequest)를 노출합니다.
     * - api 로 선언하여 이 어댑터를 의존하는 스타터/앱이 포트 타입을 함께 참조할 수 있습니다.
     */
    api(project(":libs:messaging:tc-messaging-core"))

    /*
     * Spring AMQP: RabbitTemplate, MessageConverter 등 RabbitMQ 통신 API입니다.
     * - compileOnly 로 선언하여 런타임 클래스패스는 스타터(spring-boot-starter-amqp)에서 제공합니다.
     */
    compileOnly(libs.spring.boot.starter.amqp)

    /*
     * @ConfigurationProperties, @Component 등 Spring 어노테이션 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    annotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.test {
    useJUnitPlatform()
}

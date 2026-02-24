/*
 * tc-messaging-kafka-runtime
 *
 * 역할
 * - Kafka 소비/처리 런타임 공통 구현(consumer lifecycle, runtime policy, commit bridge 등)을
 *   Business/Comm Kafka Adapter가 재사용할 수 있도록 제공하는 Kafka 전용 런타임 모듈입니다.
 *
 * 설계 의도
 * - 기존 tc-messaging-kafka-starter 내부 runtime 패키지의 재사용 대상을 분리하여
 *   Starter를 AutoConfiguration/Bean 조립 전용으로 축소합니다.
 * - "Kafka 공통화"는 이 모듈에서 수행하고, Core 모듈로 Kafka SDK가 새어 나가지 않도록 합니다.
 *
 * 현재 단계 범위
 * - 1단계(모듈 생성 + settings 등록)만 수행하므로 실제 런타임 구현 클래스는 추가하지 않습니다.
 */
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    /*
     * Java 21 툴체인 고정
     * - Kafka 런타임 공통 코드가 여러 어댑터에서 재사용되므로 버전 편차를 방지합니다.
     */
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * Kafka 런타임 구현은 공통 계약(Contract)을 public 타입으로 사용할 가능성이 높으므로
     * 우선 api로 연결합니다. 실제 구현 단계에서 노출 범위를 다시 점검할 수 있습니다.
     */
    api(project(":libs:messaging:kafka:tc-messaging-kafka-contract"))

    /*
     * 중립 소비 런타임 알고리즘(ACK/재시도/중립 커밋 추적)을 재사용하기 위한 의존성입니다.
     * 현재는 스캐폴딩 단계이므로 구현 클래스는 아직 없지만 모듈 경계를 먼저 고정합니다.
     */
    implementation(project(":libs:common:tc-common-consumer-runtime"))

    /*
     * 외부 SDK 의존성(Kafka/Spring Context 등)은 실제 런타임 클래스 구현 시점에
     * 필요한 최소 범위만 추가합니다.
     */
    /*
     * 런타임 추상 클래스의 공개/보호 메서드 시그니처에 KafkaConsumer, ConsumerRecord, TopicPartition 등이
     * 직접 등장하므로 Kafka SDK는 api로 노출합니다.
     */
    api(libs.kafka.clients)

    /*
     * AbstractKafkaConsumerLifecycle이 SmartLifecycle을 구현하므로 spring-context 타입이
     * 공개 타입 시그니처에 포함됩니다.
     */
    api(libs.spring.context)

    /*
     * 런타임 내부 로깅 구현에 사용하는 SLF4J API입니다.
     * 공개 API 시그니처에는 노출되지 않으므로 implementation으로 제한합니다.
     */
    implementation(libs.slf4j.api)
}

tasks.test {
    /*
     * 테스트 플랫폼 정책 통일
     */
    useJUnitPlatform()
}

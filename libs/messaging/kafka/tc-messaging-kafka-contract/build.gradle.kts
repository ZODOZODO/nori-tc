/*
 * tc-messaging-kafka-contract
 *
 * 역할
 * - Business/Comm Kafka Adapter가 공통으로 재사용하는 Kafka 관련 계약(Contract) 모듈입니다.
 * - DTO, 헤더 보조 유틸리티, 공통 인터페이스 등을 배치하는 위치입니다.
 *
 * 설계 의도
 * - 기존 tc-messaging-kafka-starter 내부 contract 패키지의 재사용 대상을 분리하여
 *   Starter 모듈이 "조립 전용" 역할만 수행하도록 책임을 분리합니다.
 * - Starter에 대한 직접 의존 없이 Kafka Adapter가 계약 타입만 참조할 수 있도록 합니다.
 */
plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    /*
     * 프로젝트 공통 Java 버전을 사용하여 모듈 경계 간 일관성을 유지합니다.
     */
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * 현재 단계는 모듈 스캐폴딩만 생성합니다.
     * 구현 단계에서 필요한 최소 의존성만 추가하여 계약 모듈의 경량성을 유지합니다.
     *
     * 의존성 원칙(추후 구현 시 준수):
     * - Spring Boot Starter 의존 금지
     * - 가능하면 Spring/Kafka SDK 의존 최소화 또는 제거
     */
    /*
     * KafkaHeaderSupport의 공개 메서드 시그니처가 ProducerRecord를 직접 사용하므로
     * Kafka SDK 타입은 이 모듈의 공개 API 일부입니다.
     * 따라서 하위 모듈이 별도 선언 없이 계약 타입을 사용할 수 있도록 api로 노출합니다.
     */
    api(libs.kafka.clients)
}

tasks.test {
    /*
     * 테스트 플랫폼 정책 통일
     */
    useJUnitPlatform()
}

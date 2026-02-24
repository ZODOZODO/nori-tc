/*
 * tc-common-consumer-runtime
 *
 * 역할
 * - Kafka/특정 미들웨어 SDK 타입을 노출하지 않는 "중립 소비 런타임" 공용 모듈입니다.
 * - ACK 큐, 재시도 정책, 중립 커밋 진행 추적 등 기술 비종속 알고리즘을 담는 위치입니다.
 *
 * 설계 의도
 * - tc-business-core / tc-comm-gateway-core 같은 코어 모듈이 Kafka SDK에 직접 결합되지 않도록
 *   재사용 가능한 공통 런타임 알고리즘을 이 모듈로 끌어올리기 위한 스캐폴딩입니다.
 * - 현재 단계(1단계)는 모듈 구조 생성만 수행하므로 실제 구현 클래스는 아직 추가하지 않습니다.
 */
plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    /*
     * 전체 프로젝트 표준과 동일하게 Java 21 툴체인을 고정합니다.
     * 이후 구현 단계에서 모듈 간 바이트코드 호환성 차이를 예방하기 위함입니다.
     */
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    /*
     * 현재 단계에서는 구조 스캐폴딩만 생성합니다.
     * 실제 구현 클래스 추가 시점에 다음 의존성을 재검토하여 반영합니다.
     * - slf4j-api (공통 로깅 인터페이스)
     * - junit / test fixtures (단위 테스트)
     */
    /*
     * PartitionCommitCoordinator가 디버그 로그를 출력하므로 로깅 API를 추가합니다.
     * 이 모듈의 공개 API 시그니처에는 SLF4J 타입이 노출되지 않으므로 implementation으로 제한합니다.
     */
    implementation(libs.slf4j.api)
}

tasks.test {
    /*
     * 신규 모듈에도 테스트 플랫폼 정책을 통일 적용합니다.
     * (현재 테스트 클래스는 없지만 추후 즉시 추가 가능하도록 기본값을 맞춰둡니다.)
     */
    useJUnitPlatform()
}

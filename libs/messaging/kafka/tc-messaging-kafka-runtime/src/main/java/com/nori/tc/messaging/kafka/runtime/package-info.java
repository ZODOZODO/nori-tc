/**
 * Kafka 공통 런타임 패키지입니다.
 *
 * <p>Kafka 소비자 라이프사이클, 런타임 정책, Kafka 전용 commit/seek 처리 보조 로직 등
 * Kafka SDK에 결합된 공통 구현을 배치할 예정입니다.</p>
 *
 * <p>이 패키지는 Kafka 전용 재사용 코드의 위치이며, Spring Boot 자동설정(Starter)과는
 * 책임을 분리하여 운영합니다.</p>
 */
package com.nori.tc.messaging.kafka.runtime;

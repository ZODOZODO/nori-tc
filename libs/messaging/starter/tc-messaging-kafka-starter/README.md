# tc-messaging-kafka-starter

Kafka 메시징을 조립하기 위한 AutoConfiguration 모듈입니다.

## 제공 기능
- `MessagePublisherPort` 기본 구현체 등록 (`KafkaMessagePublisher`)
- `tc.messaging.kafka.*` 설정 바인딩

## 필수 의존
- `org.springframework.kafka:spring-kafka`
- Kafka client (`org.apache.kafka:kafka-clients`)

## 설정 예시
```properties
spring.kafka.bootstrap-servers=localhost:9092

# 기본 토픽 (MessagePublishRequest.topic이 비어있을 때 사용)
tc.messaging.kafka.default-topic=tc.eqp.events

# 토픽 카탈로그(선택)
tc.messaging.kafka.topic.eqp-events=tc.eqp.events
```

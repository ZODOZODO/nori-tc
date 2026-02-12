

# tc-db-redis-starter

## 목적
- Redis 접근을 앱에서 쉽게 선택할 수 있게 하는 Starter 입니다.
- 앱은 이 starter만 의존하면 됩니다.

## 포함
- spring-boot-starter-data-redis
- starter 배타 락(fail-fast)
- 공용 RedisTemplate / CRUD Repository

## 설정 방식
- Redis 설정은 Spring 표준 프로퍼티(`spring.data.redis.*`)를 사용합니다.
- 필요한 값(host/port/password 등)을 app의 `application.yaml`에서 설정합니다.

## 제공 기능
- `tcRedisTemplate` (Bean name) : `RedisTemplate<String, Object>`
- `TcRedisCrudRepository` : 공통 CRUD 메서드 제공

```java
@Service
public class SampleService {
    private final TcRedisCrudRepository redis;

    public SampleService(TcRedisCrudRepository redis) {
        this.redis = redis;
    }

    public void save() {
        redis.set("sample:key", "value");
    }
}
```

## Fail-fast
- 실수로 starter를 2개 이상 의존하면 부팅 시 즉시 실패하도록
  동일한 Bean 이름(`tcDbStarterExclusiveLock`)을 등록합니다.
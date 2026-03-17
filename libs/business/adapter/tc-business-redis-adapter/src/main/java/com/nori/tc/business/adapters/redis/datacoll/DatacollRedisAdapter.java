package com.nori.tc.business.adapters.redis.datacoll;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Business Redis(6380) 기반 DATACOLL 수집 상태 저장소 어댑터입니다.
 *
 * <p>저장 규칙:</p>
 * <ul>
 *   <li>Redis Key: {@code tc:business:core:datacoll:{eqpId}:{correlationId}}</li>
 *   <li>Value: {@link DatacollState} (JSON 직렬화. GenericJackson2JsonRedisSerializer 사용)</li>
 *   <li>TTL: {@code tc.business.core.redis.datacoll-ttl-seconds}</li>
 * </ul>
 *
 * <p>save/update는 동일하게 덮어쓰기(overwrite)로 처리하며 TTL을 함께 갱신합니다.
 * delete 실패 시에도 TTL이 자동으로 만료되어 메모리 누수가 방지됩니다.</p>
 */
@Component
public class DatacollRedisAdapter implements DatacollStatePort {

    private static final Logger log = LoggerFactory.getLogger(DatacollRedisAdapter.class);

    /**
     * Redis key prefix. 설계 문서에서 확정된 값입니다.
     */
    private static final String DATACOLL_KEY_PREFIX = "tc:business:core:datacoll:";

    private final TcRedisCrudRepository repository;
    private final DatacollRedisProperties properties;

    /**
     * 어댑터에 필요한 의존성을 주입받습니다.
     *
     * @param repository Redis CRUD 저장소
     * @param properties DATACOLL Redis 속성 (TTL 설정 포함)
     */
    public DatacollRedisAdapter(
            final TcRedisCrudRepository repository,
            final DatacollRedisProperties properties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    /**
     * DatacollState를 Redis에 저장합니다.
     *
     * <p>동일 key가 이미 존재하면 덮어씁니다. TTL도 함께 설정됩니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @param state         저장할 DatacollState
     */
    @Override
    public void save(final String eqpId, final String correlationId, final DatacollState state) {
        Objects.requireNonNull(state, "state is null");

        final String key = buildKey(eqpId, correlationId);
        final long ttlSeconds = properties.getDatacollTtlSeconds();
        final Duration ttl = ttlSeconds > 0L ? Duration.ofSeconds(ttlSeconds) : null;

        if (ttl == null) {
            repository.set(key, state);
        } else {
            repository.set(key, state, ttl);
        }

        if (log.isDebugEnabled()) {
            log.debug("DatacollState saved to Redis. key={}, dcspecValueKeys={}, ttlSeconds={}",
                    key,
                    state.getDcspecValue().keySet(),
                    ttlSeconds > 0L ? ttlSeconds : "none");
        }
    }

    /**
     * DatacollState를 Redis에서 조회합니다.
     *
     * <p>key가 존재하지 않거나 역직렬화에 실패하면 {@link Optional#empty()}를 반환합니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @return 조회된 DatacollState. 없으면 Optional.empty()
     */
    @Override
    public Optional<DatacollState> findByKey(final String eqpId, final String correlationId) {
        final String key = buildKey(eqpId, correlationId);

        try {
            final Optional<DatacollState> result = repository.get(key, DatacollState.class);

            if (log.isDebugEnabled()) {
                if (result.isPresent()) {
                    log.debug("DatacollState found in Redis. key={}, dcspecValueKeys={}",
                            key, result.get().getDcspecValue().keySet());
                } else {
                    log.debug("DatacollState not found in Redis. key={}", key);
                }
            }

            return result;
        } catch (final Exception e) {
            // Redis 역직렬화 실패 시 Optional.empty()를 반환하고 에러 로그를 남깁니다.
            // 상위 레이어에서 DatacollState 없음으로 처리합니다.
            log.error("Failed to deserialize DatacollState from Redis. key={}, error={}",
                    key, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * DatacollState를 Redis에 갱신합니다.
     *
     * <p>COLLECT_DCDATA action 실행 후 collectionState를 갱신할 때 호출합니다.
     * 내부적으로 save와 동일하게 덮어쓰기(overwrite)로 처리하며, TTL도 갱신됩니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @param state         갱신할 DatacollState
     */
    @Override
    public void update(final String eqpId, final String correlationId, final DatacollState state) {
        // 갱신은 덮어쓰기와 동일하게 처리합니다. TTL도 함께 갱신합니다.
        save(eqpId, correlationId, state);
    }

    /**
     * DatacollState Redis key를 즉시 삭제합니다.
     *
     * <p>DATACOLL 보고 완료 후 호출됩니다. 삭제 실패 시에도 TTL이 자동으로 만료됩니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     */
    @Override
    public void delete(final String eqpId, final String correlationId) {
        final String key = buildKey(eqpId, correlationId);
        final boolean deleted = repository.delete(key);

        if (log.isDebugEnabled()) {
            log.debug("DatacollState deleted from Redis. key={}, deleted={}", key, deleted);
        }
    }

    /**
     * Redis key를 생성합니다.
     *
     * <p>포맷: {@code tc:business:core:datacoll:{eqpId}:{correlationId}}</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @return Redis key 문자열
     */
    private String buildKey(final String eqpId, final String correlationId) {
        return DATACOLL_KEY_PREFIX + eqpId + ":" + correlationId;
    }
}

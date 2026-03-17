package com.nori.tc.business.adapters.redis.datacoll;

import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.business.domain.datacoll.ItemCollectionState;
import com.nori.tc.db.domain.common.model.DcopCollectionRule;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DatacollRedisAdapter} 단위 테스트입니다.
 *
 * <p>{@link TcRedisCrudRepository}를 직접 구현한 인메모리 스텁을 사용하여
 * 외부 Redis 의존 없이 어댑터 동작을 검증합니다.</p>
 */
class DatacollRedisAdapterTest {

    /** 테스트용 Redis 스텁 */
    private StubTcRedisCrudRepository stubRepository;
    private DatacollRedisProperties properties;
    private DatacollRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        stubRepository = new StubTcRedisCrudRepository();
        properties = new DatacollRedisProperties();
        properties.setDatacollTtlSeconds(3600L);
        adapter = new DatacollRedisAdapter(stubRepository, properties);
    }

    // -------------------------------------------------------------------------
    // save
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save: DatacollState를 Redis에 저장하면 동일 key로 조회할 수 있다")
    void save_shouldStoreStateInRedis() {
        // given
        final DatacollState state = createSampleState();

        // when
        adapter.save("EQP01", "CORR-001", state);

        // then
        final Object stored = stubRepository.storage.get("tc:business:core:datacoll:EQP01:CORR-001");
        assertNotNull(stored, "저장된 값이 null이면 안 됩니다");
        assertInstanceOf(DatacollState.class, stored);
    }

    @Test
    @DisplayName("save: TTL이 0이면 TTL 없이 저장된다")
    void save_withZeroTtl_shouldStoreWithoutTtl() {
        // given
        properties.setDatacollTtlSeconds(0L);
        final DatacollState state = createSampleState();

        // when
        adapter.save("EQP01", "CORR-001", state);

        // then - 저장 자체는 성공, TTL은 null로 호출됨
        assertTrue(stubRepository.storage.containsKey("tc:business:core:datacoll:EQP01:CORR-001"));
        assertNull(stubRepository.lastTtl, "TTL 0이면 TTL 없이 저장해야 합니다");
    }

    @Test
    @DisplayName("save: state가 null이면 NullPointerException이 발생한다")
    void save_withNullState_shouldThrowException() {
        assertThrows(NullPointerException.class,
                () -> adapter.save("EQP01", "CORR-001", null));
    }

    // -------------------------------------------------------------------------
    // findByKey
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByKey: 저장된 DatacollState를 key로 조회할 수 있다")
    void findByKey_whenExists_shouldReturnState() {
        // given
        final DatacollState state = createSampleState();
        adapter.save("EQP01", "CORR-001", state);

        // when
        final Optional<DatacollState> result = adapter.findByKey("EQP01", "CORR-001");

        // then
        assertTrue(result.isPresent(), "저장된 값은 조회 가능해야 합니다");
        assertFalse(result.get().getDcspecValue().isEmpty());
    }

    @Test
    @DisplayName("findByKey: key가 없으면 Optional.empty()를 반환한다")
    void findByKey_whenNotExists_shouldReturnEmpty() {
        // when
        final Optional<DatacollState> result = adapter.findByKey("EQP01", "NOT-EXIST");

        // then
        assertFalse(result.isPresent(), "존재하지 않는 key는 Optional.empty()여야 합니다");
    }

    @Test
    @DisplayName("findByKey: 역직렬화 실패 시 Optional.empty()를 반환하고 예외를 전파하지 않는다")
    void findByKey_whenDeserializationFails_shouldReturnEmpty() {
        // given - String 타입(비호환)을 강제로 저장
        stubRepository.storage.put("tc:business:core:datacoll:EQP01:BAD-KEY", "not-a-datacoll-state");

        // when
        final Optional<DatacollState> result = adapter.findByKey("EQP01", "BAD-KEY");

        // then - ClassCastException 발생해도 empty 반환 (예외 전파 X)
        assertFalse(result.isPresent(), "역직렬화 실패 시 Optional.empty()여야 합니다");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update: 기존 DatacollState를 덮어쓰고 TTL을 갱신한다")
    void update_shouldOverwriteExistingState() {
        // given
        final DatacollState initialState = createSampleState();
        adapter.save("EQP01", "CORR-001", initialState);

        // collectionState에 LAST 규칙 값 추가
        final DatacollState updatedState = createSampleState();
        updatedState.getCollectionState().put("Temperature",
                ItemCollectionState.ofValue(DcopCollectionRule.LAST, "25.5"));

        // when
        adapter.update("EQP01", "CORR-001", updatedState);

        // then
        final Optional<DatacollState> result = adapter.findByKey("EQP01", "CORR-001");
        assertTrue(result.isPresent());
        assertTrue(result.get().getCollectionState().containsKey("Temperature"),
                "갱신된 collectionState가 저장되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("delete: 저장된 DatacollState를 삭제하면 이후 조회 시 Optional.empty()를 반환한다")
    void delete_shouldRemoveStateFromRedis() {
        // given
        adapter.save("EQP01", "CORR-001", createSampleState());
        assertTrue(adapter.findByKey("EQP01", "CORR-001").isPresent());

        // when
        adapter.delete("EQP01", "CORR-001");

        // then
        assertFalse(adapter.findByKey("EQP01", "CORR-001").isPresent(),
                "삭제 후에는 조회되지 않아야 합니다");
    }

    @Test
    @DisplayName("delete: 존재하지 않는 key 삭제 시 예외가 발생하지 않는다")
    void delete_whenNotExists_shouldNotThrow() {
        // when / then - 예외 없이 정상 종료
        assertDoesNotThrow(() -> adapter.delete("EQP01", "NOT-EXIST"));
    }

    // -------------------------------------------------------------------------
    // Key 포맷 검증
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save: Redis key 포맷이 tc:business:core:datacoll:{eqpId}:{correlationId}인지 확인")
    void save_shouldUseCorrectKeyFormat() {
        // when
        adapter.save("EQP-TEST", "CORR-XYZ", createSampleState());

        // then
        assertTrue(stubRepository.storage.containsKey("tc:business:core:datacoll:EQP-TEST:CORR-XYZ"),
                "Redis key 포맷이 설계 문서와 일치해야 합니다");
    }

    @Test
    @DisplayName("save: TTL이 설정된 경우 Duration으로 변환되어 저장된다")
    void save_withPositiveTtl_shouldApplyTtl() {
        // given
        properties.setDatacollTtlSeconds(86400L);

        // when
        adapter.save("EQP01", "CORR-001", createSampleState());

        // then
        assertEquals(Duration.ofSeconds(86400L), stubRepository.lastTtl,
                "설정된 TTL이 Duration으로 적용되어야 합니다");
    }

    // -------------------------------------------------------------------------
    // 헬퍼
    // -------------------------------------------------------------------------

    private DatacollState createSampleState() {
        final Map<String, String> dcspecValue = new HashMap<>();
        dcspecValue.put("Temperature", "");
        dcspecValue.put("Humidity", "");

        final DatacollState state = new DatacollState(dcspecValue, new HashMap<>());

        // AVERAGE 수집 상태 예시
        state.getCollectionState().put("Humidity",
                ItemCollectionState.ofAverageFirst(new BigDecimal("60.0")));

        return state;
    }

    // -------------------------------------------------------------------------
    // 테스트용 TcRedisCrudRepository 스텁
    // -------------------------------------------------------------------------

    /**
     * 외부 Redis 없이 단위 테스트를 실행하기 위한 인메모리 저장소 스텁입니다.
     *
     * <p>저장/조회/삭제 동작을 HashMap으로 시뮬레이션합니다.
     * TTL은 실제로 적용되지 않으며, 마지막으로 설정된 TTL 값을 {@code lastTtl}에 기록합니다.</p>
     */
    static class StubTcRedisCrudRepository implements TcRedisCrudRepository {

        final Map<String, Object> storage = new HashMap<>();
        Duration lastTtl = null;

        @Override
        public void set(final String key, final Object value) {
            storage.put(key, value);
            lastTtl = null;
        }

        @Override
        public void set(final String key, final Object value, final Duration ttl) {
            storage.put(key, value);
            lastTtl = ttl;
        }

        @Override
        public boolean setIfAbsent(final String key, final Object value, final Duration ttl) {
            if (storage.containsKey(key)) {
                return false;
            }
            storage.put(key, value);
            lastTtl = ttl;
            return true;
        }

        @Override
        public Optional<Object> get(final String key) {
            return Optional.ofNullable(storage.get(key));
        }

        @Override
        public <T> Optional<T> get(final String key, final Class<T> type) {
            final Object value = storage.get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
            // 타입 불일치 → 실제 Redis에서 역직렬화 실패 상황을 시뮬레이션합니다.
            throw new ClassCastException("Cannot cast " + value.getClass() + " to " + type);
        }

        @Override
        public boolean delete(final String key) {
            return storage.remove(key) != null;
        }

        @Override
        public long delete(final java.util.Collection<String> keys) {
            return keys.stream().filter(k -> storage.remove(k) != null).count();
        }

        @Override
        public boolean exists(final String key) {
            return storage.containsKey(key);
        }

        @Override
        public boolean expire(final String key, final Duration ttl) {
            return storage.containsKey(key);
        }

        @Override
        public Long increment(final String key, final long delta) {
            throw new UnsupportedOperationException("테스트 스텁에서 지원하지 않습니다");
        }

        @Override
        public Map<String, Object> multiGet(final java.util.Collection<String> keys) {
            throw new UnsupportedOperationException("테스트 스텁에서 지원하지 않습니다");
        }

        @Override
        public void multiSet(final Map<String, Object> values, final Duration ttl) {
            throw new UnsupportedOperationException("테스트 스텁에서 지원하지 않습니다");
        }
    }
}

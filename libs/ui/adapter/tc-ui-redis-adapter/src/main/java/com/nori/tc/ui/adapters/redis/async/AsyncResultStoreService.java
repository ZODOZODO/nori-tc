package com.nori.tc.ui.adapters.redis.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.ui.core.model.UiCommandReply;
import com.nori.tc.ui.core.port.redis.AsyncResultStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Business Redis(6380)를 이용한 비동기 작업 결과 저장 서비스입니다.
 *
 * <p>{@link AsyncResultStorePort}를 구현하며, eqp_start/eqp_end 요청에 대한
 * gateway 응답(tc.ui.commands 수신)을 임시 저장합니다.</p>
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>Front → POST /api/eqp/{id}/start — UI Backend가 202 즉시 반환</li>
 *   <li>Gateway가 tc.ui.commands에 EQP_START_REP 메시지 발행</li>
 *   <li>{@code UiCommandIngressService}가 이 서비스의 {@code save()}를 호출하여 결과 저장</li>
 *   <li>Front → GET /api/async/{traceId} — polling으로 결과 조회</li>
 * </ol>
 *
 * <p>키 형식: {@code tc:ui:backend:async:{traceId}}</p>
 * <p>TTL: {@code tc.ui.backend.async.result-ttl-seconds} 설정값 (기본 600초)</p>
 *
 * <p>{@link UiCommandReply}는 Redis 저장 시 {@link RedisUiAsyncResultEntry}로 변환하여
 * JSON 직렬화 방식으로 저장합니다.</p>
 */
@Service
@EnableConfigurationProperties(UiAsyncProperties.class)
public class AsyncResultStoreService implements AsyncResultStorePort {

    private static final Logger log = LoggerFactory.getLogger(AsyncResultStoreService.class);

    /** Redis 비동기 결과 키 접두사 */
    private static final String KEY_PREFIX = "tc:ui:backend:async:";

    private final RedisTemplate<String, Object> businessRedisTemplate;
    private final UiAsyncProperties asyncProperties;
    private final ObjectMapper redisObjectMapper;

    public AsyncResultStoreService(
            @Qualifier("businessRedisTemplate") final RedisTemplate<String, Object> businessRedisTemplate,
            final UiAsyncProperties asyncProperties,
            @Qualifier("uiRedisObjectMapper") final ObjectMapper redisObjectMapper
    ) {
        this.businessRedisTemplate = Objects.requireNonNull(businessRedisTemplate,
                "businessRedisTemplate 은 null 이 될 수 없습니다.");
        this.asyncProperties = Objects.requireNonNull(asyncProperties,
                "asyncProperties 는 null 이 될 수 없습니다.");
        this.redisObjectMapper = Objects.requireNonNull(redisObjectMapper,
                "redisObjectMapper 는 null 이 될 수 없습니다.");
    }

    /**
     * 비동기 작업 결과를 Redis에 저장합니다.
     *
     * <p>{@code UiCommandIngressService}가 tc.ui.commands에서
     * EQP_START_REP / EQP_END_REP 메시지를 수신했을 때 호출합니다.</p>
     *
     * @param traceId 작업 추적 ID (ULID, 캐시 키 생성에 사용)
     * @param reply   저장할 명령 응답 DTO
     */
    @Override
    public void save(final String traceId, final UiCommandReply reply) {
        Objects.requireNonNull(traceId, "traceId 는 null 이 될 수 없습니다.");
        Objects.requireNonNull(reply, "reply 는 null 이 될 수 없습니다.");

        final String key = KEY_PREFIX + traceId;
        final long ttlSeconds = asyncProperties.getResultTtlSeconds();
        final RedisUiAsyncResultEntry entry = RedisUiAsyncResultEntry.from(reply);

        try {
            if (ttlSeconds > 0L) {
                businessRedisTemplate.opsForValue().set(key, entry, Duration.ofSeconds(ttlSeconds));
            } else {
                // TTL=0 은 만료 없이 보관 (설정에 따라 허용)
                businessRedisTemplate.opsForValue().set(key, entry);
            }
            log.info("비동기 결과 저장 완료. traceId={}, eqpId={}, status={}, ttlSeconds={}",
                    traceId,
                    reply.eqpId(),
                    reply.status(),
                    ttlSeconds > 0L ? ttlSeconds : "무제한");
        } catch (Exception e) {
            // 저장 실패 시 오류 로그만 남기고 상위 예외는 전파합니다.
            // polling 시 결과가 없으면 front에서 재시도 또는 타임아웃 처리를 합니다.
            log.error("비동기 결과 저장 실패. traceId={}, eqpId={}",
                    traceId, reply.eqpId(), e);
            throw e;
        }
    }

    /**
     * 비동기 작업 결과를 조회합니다.
     *
     * <p>{@code AsyncResultController}가 front의 polling 요청을 처리할 때 호출합니다.
     * 결과가 없으면 아직 처리 중이거나 TTL이 만료된 것으로 간주합니다.</p>
     *
     * @param traceId 조회할 작업 추적 ID
     * @return 저장된 결과가 있으면 {@link UiCommandReply}, 없으면 빈 Optional
     */
    @Override
    public Optional<UiCommandReply> get(final String traceId) {
        Objects.requireNonNull(traceId, "traceId 는 null 이 될 수 없습니다.");

        final String key = KEY_PREFIX + traceId;
        try {
            final Object raw = businessRedisTemplate.opsForValue().get(key);
            if (raw == null) {
                if (log.isDebugEnabled()) {
                    log.debug("비동기 결과 없음 (처리 중이거나 TTL 만료). traceId={}, key={}", traceId, key);
                }
                return Optional.empty();
            }

            if (raw instanceof RedisUiAsyncResultEntry entry) {
                if (log.isDebugEnabled()) {
                    log.debug("비동기 결과 조회 성공. traceId={}, status={}", traceId, entry.getStatus());
                }
                return Optional.of(entry.toReplyMessage());
            }
            if (raw instanceof java.util.Map<?, ?> map) {
                // 직렬화 포맷 전환 과정에서 LinkedHashMap으로 역직렬화되는 경우를 방어합니다.
                final RedisUiAsyncResultEntry entry = redisObjectMapper.convertValue(map, RedisUiAsyncResultEntry.class);
                if (log.isDebugEnabled()) {
                    log.debug("비동기 결과 조회 성공(Map 변환). traceId={}, status={}", traceId, entry.getStatus());
                }
                return Optional.of(entry.toReplyMessage());
            }

            // 타입 불일치 — 직렬화 형식 변경 등으로 발생 가능
            log.warn("비동기 결과 타입 불일치, 해당 항목 제거. key={}, actualType={}",
                    key, raw.getClass().getName());
            businessRedisTemplate.delete(key);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("비동기 결과 조회 실패. traceId={}, key={}", traceId, key, e);
            return Optional.empty();
        }
    }
}

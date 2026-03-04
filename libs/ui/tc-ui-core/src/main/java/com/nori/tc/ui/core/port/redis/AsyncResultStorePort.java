package com.nori.tc.ui.core.port.redis;

import com.nori.tc.ui.core.model.UiCommandReply;

import java.util.Optional;

/**
 * 비동기 작업 결과 임시 저장 포트입니다.
 *
 * <p>역할:</p>
 * <p>eqp_start / eqp_end 요청에 대한 gateway 응답(tc.ui.commands 수신)을
 * Business Redis(6380)에 임시 저장합니다.
 * front는 202 즉시 반환 후 GET /api/async/{traceId}로 polling하여 결과를 확인합니다.</p>
 *
 * <p>캐시 키 형식:</p>
 * <pre>tc:ui:backend:async:{traceId}</pre>
 *
 * <p>TTL:</p>
 * <p>{@code tc.ui.backend.async.result-ttl-seconds} 설정값을 따릅니다 (기본 600초).</p>
 *
 * <p>구현체:</p>
 * <p>tc-ui-redis-adapter의 AsyncResultStoreService가 이 인터페이스를 구현합니다.</p>
 */
public interface AsyncResultStorePort {

    /**
     * 비동기 작업 결과를 Redis에 저장합니다.
     *
     * <p>UiCommandIngressService가 tc.ui.commands에서 EQP_START_REP / EQP_END_REP
     * 메시지를 수신했을 때 호출합니다. TTL은 구현체가 설정에서 읽어 적용합니다.</p>
     *
     * @param traceId 작업 추적 ID (ULID, 캐시 키 생성에 사용)
     * @param reply   저장할 명령 응답 DTO (status, errorCode, errorMsg 포함)
     */
    void save(String traceId, UiCommandReply reply);

    /**
     * 비동기 작업 결과를 조회합니다.
     *
     * <p>AsyncResultController가 front의 polling 요청을 처리할 때 호출합니다.
     * 결과가 없으면 아직 처리 중이거나 TTL이 만료된 것으로 간주합니다.</p>
     *
     * @param traceId 조회할 작업 추적 ID
     * @return 저장된 결과가 있으면 UiCommandReply, 없으면 빈 Optional
     */
    Optional<UiCommandReply> get(String traceId);

    /**
     * 테스트 또는 초기 구성에 사용할 noop(아무 동작 없음) 구현체를 반환합니다.
     *
     * @return 저장을 무시하고 항상 빈 Optional을 반환하는 noop 구현체
     */
    static AsyncResultStorePort noop() {
        return new AsyncResultStorePort() {
            @Override
            public void save(final String traceId, final UiCommandReply reply) {
                // noop: 아무 동작 없음
            }

            @Override
            public Optional<UiCommandReply> get(final String traceId) {
                return Optional.empty();
            }
        };
    }
}

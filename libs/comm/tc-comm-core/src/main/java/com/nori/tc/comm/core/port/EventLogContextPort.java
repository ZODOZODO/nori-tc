package com.nori.tc.comm.core.port;

/**
 * 이벤트 단위 로그 컨텍스트(MDC 등) 스코프를 여는 포트입니다.
 *
 * <p>core 모듈은 로깅 구현체(logback/MDC 유틸)에 직접 의존하지 않고,
 * "이벤트 처리 구간을 traceId/eqpId로 묶어준다"는 의도만 이 포트를 통해 표현합니다.</p>
 *
 * <p>구현체 예시:</p>
 * <p>- Gateway: eqpId + traceId 를 MDC에 주입하는 스코프 반환</p>
 * <p>- 테스트/비적용 환경: no-op 스코프 반환</p>
 */
@FunctionalInterface
public interface EventLogContextPort {

    /**
     * 이벤트 단위 로그 컨텍스트를 열 때 필요한 관측 메타데이터 묶음입니다.
     *
     * <p>core는 이 구조체를 통해 "어떤 이벤트를 처리 중인지"만 전달하고,
     * 실제 MDC 주입/관측 로그 출력 정책은 gateway 구현체가 결정합니다.</p>
     *
     * @param eqpId         설비 ID
     * @param traceId       이벤트 traceId
     * @param eventType     파싱된 이벤트 타입(messageName)
     * @param interfaceType 통신 인터페이스 타입(HSMS/SOCKET)
     * @param socketType    SOCKET 세부 타입(line-delimited 등, 없으면 null 가능)
     */
    record EventLogContextRequest(
            String eqpId,
            String traceId,
            String eventType,
            String interfaceType,
            String socketType
    ) {
    }

    /**
     * 이벤트 생명주기 로그 스코프를 엽니다.
     *
     * <p>반환된 {@link AutoCloseable}은 호출 측에서 반드시 닫아야 하며,
     * 닫을 때 이전 로그 컨텍스트를 복구하는 책임을 가질 수 있습니다.</p>
     *
     * @param eqpId   설비 ID (로그 상관관계용)
     * @param traceId 이벤트 traceId (로그 상관관계용)
     * @return 닫을 수 있는 로그 컨텍스트 스코프 (null 반환 금지 권장)
     */
    AutoCloseable open(EventLogContextRequest request);

    /**
     * 메타데이터가 없는 단순 컨텍스트 오픈이 필요한 호출부를 위한 편의 메서드입니다.
     *
     * <p>기존 시그니처 호환을 위해 남겨 두며, 내부적으로는 요청 객체 기반 추상 메서드를 호출합니다.</p>
     *
     * @param eqpId   설비 ID
     * @param traceId 이벤트 traceId
     * @return 닫을 수 있는 로그 컨텍스트 스코프
     */
    default AutoCloseable open(final String eqpId, final String traceId) {
        return open(new EventLogContextRequest(eqpId, traceId, null, null, null));
    }

    /**
     * 아무 동작도 하지 않는 기본 구현을 반환합니다.
     *
     * <p>core 단위 테스트나 아직 MDC 전파를 연결하지 않은 환경에서 안전하게 사용할 수 있습니다.</p>
     *
     * @return no-op 이벤트 로그 컨텍스트 포트
     */
    static EventLogContextPort noOp() {
        return request -> NoOpCloseable.INSTANCE;
    }

    /**
     * no-op closeable 구현입니다.
     *
     * <p>{@link AutoCloseable#close()} 호출 시 아무 동작도 하지 않습니다.</p>
     */
    enum NoOpCloseable implements AutoCloseable {
        /**
         * 싱글턴 인스턴스입니다.
         */
        INSTANCE;

        /**
         * no-op close 구현입니다.
         */
        @Override
        public void close() {
            // no-op
        }
    }
}

package com.nori.tc.comm.core.port;

/**
 * traceId(ULID 등) 생성 Port
 *
 * 목적
 * - traceId 생성 전략을 core에서 고정하지 않기 위해 Port로 분리합니다.
 * - 기본 구현은 tc-comm-domain(shared kernel)의 UlidGenerator를 사용하면 됩니다.
 */
public interface TraceIdGeneratorPort {
    
    /**
     * 통신 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    String newTraceId();
}

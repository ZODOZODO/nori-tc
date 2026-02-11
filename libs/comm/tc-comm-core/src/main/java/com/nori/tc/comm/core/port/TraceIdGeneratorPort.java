package com.nori.tc.comm.core.port;

/**
 * traceId(ULID 등) 생성 Port
 *
 * 목적
 * - traceId 생성 전략을 core에서 고정하지 않기 위해 Port로 분리합니다.
 * - 기본 구현은 tc-comm-domain(shared kernel)의 UlidGenerator를 사용하면 됩니다.
 */
public interface TraceIdGeneratorPort {
    String newTraceId();
}

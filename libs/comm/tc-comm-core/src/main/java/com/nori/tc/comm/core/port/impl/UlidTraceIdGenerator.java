package com.nori.tc.comm.core.port.impl;

import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.gateway.domain.util.UlidGenerator;

/**
 * ULID 기반 traceId 생성(기본)
 *
 * - shared kernel(tc-comm-domain)에 있는 UlidGenerator를 사용합니다.
 * - 생성 규칙을 바꾸고 싶으면(예: monotonic ULID, UUIDv7 등) Port 구현만 교체하면 됩니다.
 */
public final class UlidTraceIdGenerator implements TraceIdGeneratorPort {

    
    /**
     * 통신 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    @Override
    public String newTraceId() {
        return UlidGenerator.newUlid();
    }
}

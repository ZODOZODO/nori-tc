package com.nori.tc.comm.core.port.impl;

import com.nori.tc.comm.core.port.TraceNoGeneratorPort;
import com.nori.tc.comm.domain.util.UlidGenerator;

/**
 * ULID 기반 traceNo 생성(기본)
 *
 * - shared kernel(tc-comm-domain)에 있는 UlidGenerator를 사용합니다.
 * - 생성 규칙을 바꾸고 싶으면(예: monotonic ULID, UUIDv7 등) Port 구현만 교체하면 됩니다.
 */
public final class UlidTraceNoGenerator implements TraceNoGeneratorPort {

    @Override
    public String newTraceNo() {
        return UlidGenerator.newUlid();
    }
}

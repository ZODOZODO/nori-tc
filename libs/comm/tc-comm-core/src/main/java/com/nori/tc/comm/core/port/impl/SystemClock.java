package com.nori.tc.comm.core.port.impl;

import com.nori.tc.comm.core.port.ClockPort;

/**
 * 시스템 시계 구현체(기본)
 *
 * - core 엔진은 ClockPort만 알도록 설계합니다.
 * - 운영에서는 SystemClock을 주입하고,
 * - 테스트에서는 고정 시계(FixedClock 등)를 별도로 만들어 주입하면 됩니다.
 */
public final class SystemClock implements ClockPort {

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    @Override
    public long nowEpochMillis() {
        return System.currentTimeMillis();
    }
}

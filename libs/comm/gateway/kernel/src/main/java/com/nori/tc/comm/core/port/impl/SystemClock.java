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

    @Override
    public long nowEpochMillis() {
        return System.currentTimeMillis();
    }
}

package com.nori.tc.comm.core.port;

/**
 * 시간 제공 Port
 *
 * 목적
 * - core 엔진에서 System.currentTimeMillis()를 직접 호출하면 테스트가 어려워집니다.
 * - app에서 실제 구현(SystemClock)을 주입하고, 테스트에서는 고정 시계를 주입합니다.
 */
public interface ClockPort {
    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    long nowEpochMillis();
}

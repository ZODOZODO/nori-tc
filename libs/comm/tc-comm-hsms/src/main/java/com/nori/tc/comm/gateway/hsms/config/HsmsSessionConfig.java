package com.nori.tc.comm.gateway.hsms.config;

/**
 * HSMS 세션 설정(설비별)
 *
 * 포함 항목
 * - deviceId: HSMS 헤더의 Device ID
 * - timers: T3~T8
 * - linktestEnabled / linktestIntervalMs: Linktest 주기적 송신(옵션)
 * - maxFrameBytes: 프레임 단위 상한(폭주/비정상 프레임 방어)
 * - requireSelectBeforeData: Selected 상태가 아니면 DATA 메시지를 처리하지 않을지 여부
 *
 * 주의
 * - maxFrameBytes는 "HSMS 프레임 단위" 상한입니다.
 * - reassemblyBuffer 상한과 함께 운영 정책으로 사용하십시오.
 */
public record HsmsSessionConfig(
        int deviceId,
        HsmsTimerConfig timers,
        boolean linktestEnabled,
        long linktestIntervalMs,
        int maxFrameBytes,
        boolean requireSelectBeforeData
) {
    public HsmsSessionConfig {
        if (deviceId < 0 || deviceId > 0xFFFF) {
            throw new IllegalArgumentException("deviceId must be 0..65535");
        }
        if (timers == null) throw new IllegalArgumentException("timers is required");
        if (linktestEnabled && linktestIntervalMs <= 0) {
            throw new IllegalArgumentException("linktestIntervalMs must be > 0 when enabled");
        }
        if (maxFrameBytes <= 0) throw new IllegalArgumentException("maxFrameBytes must be > 0");
    }

    /**
     * 권장 기본값(운영 시작점)
     * - 무유실/저지연을 위해 "불필요한 제약"은 줄이되, 상한(maxFrameBytes)은 반드시 둡니다.
     */
    public static HsmsSessionConfig recommendedDefaults(final int deviceId) {
        // 입력/상태를 확인한 뒤 핵심 로직을 수행하고 결과를 정리합니다.
        return new HsmsSessionConfig(
                deviceId,
                HsmsTimerConfig.recommendedDefaults(),
                true,
                30_000,     // Linktest interval (common starting point)
                256 * 1024, // 프레임 최대 256KB(프로젝트 기본 상한과 일치)
                true        // Selected 상태에서만 DATA 처리(안전 우선)
        );
    }
}

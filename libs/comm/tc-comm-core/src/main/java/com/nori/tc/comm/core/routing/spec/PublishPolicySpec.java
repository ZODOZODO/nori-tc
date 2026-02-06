package com.nori.tc.comm.core.routing.spec;

import com.nori.tc.comm.core.routing.PublishMode;

import java.util.List;

/**
 * PublishPolicy 스펙(동적 로딩 대상)
 *
 * 운영 방식(권장)
 * - spec.version / spec.updatedAtEpochMs 를 기준으로 폴링 로드
 * - 로드 성공 시 Atomic swap으로 PublishPolicyEngine 교체
 * - 실패 시 직전 정상 버전 유지
 *
 * defaultMode 권장값
 * - 무유실 우선: OUTBOX
 */
public record PublishPolicySpec(
        String version,
        long updatedAtEpochMs,
        PublishMode defaultMode,
        List<PublishPolicyRule> rules
) {
    public PublishPolicySpec {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version is required");
        if (defaultMode == null) throw new IllegalArgumentException("defaultMode is required");
        if (rules == null) rules = List.of();
    }
}

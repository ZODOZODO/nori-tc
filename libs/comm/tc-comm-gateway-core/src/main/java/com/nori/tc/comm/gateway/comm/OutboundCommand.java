package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.message.OutboundRawFrame;

/**
 * Outbound command wrapper for retry control.
 */
public record OutboundCommand(
        OutboundRawFrame frame,
        int attempt,
        long createdAtEpochMs
) {
    public OutboundCommand {
        if (frame == null) {
            throw new IllegalArgumentException("frame is null");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public OutboundCommand nextAttempt() {
        return new OutboundCommand(frame, attempt + 1, createdAtEpochMs);
    }
}

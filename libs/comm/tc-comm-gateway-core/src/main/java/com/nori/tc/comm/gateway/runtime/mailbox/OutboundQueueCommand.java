package com.nori.tc.comm.gateway.runtime.mailbox;

import com.nori.tc.comm.core.message.OutboundRawFrame;

/**
 * outbound 큐에 적재되는 송신 명령 레코드입니다.
 *
 * <p>재시도 제어를 위해 raw frame과 함께 시도 횟수/최초 생성 시각을 보관합니다.</p>
 *
 * @param frame 전송 대상 raw frame
 * @param attempt 현재 재시도 횟수(최초 0)
 * @param createdAtEpochMs 최초 큐 적재 시각(epoch millis)
 */
public record OutboundQueueCommand(
        OutboundRawFrame frame,
        int attempt,
        long createdAtEpochMs
) {
    public OutboundQueueCommand {
        if (frame == null) {
            throw new IllegalArgumentException("frame is null");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }
    }

    /**
     * 동일 프레임에 대한 다음 재시도 명령을 생성합니다.
     *
     * <p>최초 생성 시각은 유지하여 end-to-end 지연 계산 기준점을 보존합니다.</p>
     *
     * @return 시도 횟수가 1 증가한 신규 명령
     */
    public OutboundQueueCommand nextAttempt() {
        return new OutboundQueueCommand(frame, attempt + 1, createdAtEpochMs);
    }
}

package com.nori.tc.comm.gateway.comm;

import com.nori.tc.common.mailbox.MailboxTask;

import java.util.Objects;

/**
 * 게이트웨이 설비 단위 스케줄링 신호(task)입니다.
 *
 * <p>실제 비즈니스 데이터는 {@link EqpMailbox} 내부 큐(inbound/outbound)에 저장되고,
 * 본 task는 "해당 eqpId를 worker가 한 번 처리해야 한다"는 스케줄링 토큰 역할만 수행합니다.</p>
 *
 * @param eqpId 설비 식별자(라우팅 키)
 * @param createdAtEpochMs 스케줄링 요청 시각(epoch millis)
 */
public record EqpMailboxScheduleTask(
        String eqpId,
        long createdAtEpochMs
) implements MailboxTask {

    /**
     * 생성 시 입력 유효성을 검증합니다.
     */
    public EqpMailboxScheduleTask {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        if (createdAtEpochMs < 0L) {
            throw new IllegalArgumentException("createdAtEpochMs must be >= 0");
        }
        eqpId = eqpId.trim();
    }

    /**
     * 공통 MailboxScheduler가 사용하는 라우팅 키를 반환합니다.
     *
     * @return eqpId 라우팅 키
     */
    @Override
    public String routingKey() {
        return Objects.requireNonNull(eqpId, "eqpId is null");
    }
}

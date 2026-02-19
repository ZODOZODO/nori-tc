package com.nori.tc.common.kafka.processing;

/**
 * 작업 처리 결과를 나타내는 ack 상태값입니다.
 *
 * <p>모든 상태가 즉시 Kafka offset 커밋 대상은 아니기 때문에
 * commit 가능 여부를 상태에 명시적으로 포함합니다.</p>
 */
public enum AckStatus {

    /**
     * 비즈니스 처리가 정상 완료되었습니다.
     * 커밋 가능 상태입니다.
     */
    SUCCESS(true),

    /**
     * 처리는 실패했지만 DLQ로 이관되어 현재 소비 흐름에서는 종료로 간주합니다.
     * 커밋 가능 상태입니다.
     */
    DLQ(true),

    /**
     * 처리 실패 후 재시도가 예약되었습니다.
     * 아직 커밋하면 안 되는 상태입니다.
     */
    RETRY_SCHEDULED(false),

    /**
     * 처리 실패 후 DLQ 이관 없이 종료되었습니다.
     * 커밋 불가 상태입니다.
     */
    FAILED(false);

    private final boolean commitEligible;

    AckStatus(final boolean commitEligible) {
        this.commitEligible = commitEligible;
    }

    /**
     * 이 상태가 Kafka 커밋 오프셋을 전진시켜도 되는지 반환합니다.
     *
     * @return 커밋 가능 여부
     */
    public boolean isCommitEligible() {
        return commitEligible;
    }
}

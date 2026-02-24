package com.nori.tc.common.consumer.runtime;

/**
 * 작업 처리 결과를 소비 루프에 전달하기 위한 ACK 상태 값입니다.
 *
 * <p>모든 상태가 즉시 커밋 가능하지는 않으므로, 상태 자체가 커밋 가능 여부를 함께 보유합니다.</p>
 */
public enum AckStatus {

    /**
     * 작업이 정상 처리되었고 커밋을 진행할 수 있는 상태입니다.
     */
    SUCCESS(true),

    /**
     * 작업이 DLQ로 정상 전환되어 현재 소비 흐름 관점에서는 종료 처리 가능한 상태입니다.
     */
    DLQ(true),

    /**
     * 재시도가 예약되어 아직 커밋하면 안 되는 상태입니다.
     */
    RETRY_SCHEDULED(false),

    /**
     * 실패했으며 커밋을 진행하면 안 되는 상태입니다.
     */
    FAILED(false);

    private final boolean commitEligible;

    /**
     * enum 상수별 커밋 가능 여부를 초기화합니다.
     *
     * @param commitEligible 커밋 가능 여부
     */
    AckStatus(final boolean commitEligible) {
        this.commitEligible = commitEligible;
    }

    /**
     * 현재 상태가 커밋 오프셋 전진 대상인지 반환합니다.
     *
     * @return 커밋 가능 여부
     */
    public boolean isCommitEligible() {
        return commitEligible;
    }
}

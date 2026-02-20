package com.nori.tc.comm.gateway.lifecycle;

/**
 * lifecycle 전이 결과 소비자 인터페이스입니다.
 *
 * <p>core 상태머신이 확정한 START/END 결과를 외부 어댑터(UI 지연 응답 발행 등)로
 * 전달할 때 사용하는 확장 포인트입니다.</p>
 */
@FunctionalInterface
public interface EqpLifecycleOutcomeListener {

    /**
     * lifecycle 전이 결과를 전달받아 후속 처리를 수행합니다.
     *
     * @param outcome lifecycle 전이 확정 결과
     */
    void onOutcome(EqpLifecycleOutcome outcome);

    /**
     * 아무 동작도 수행하지 않는 기본 리스너를 반환합니다.
     *
     * @return no-op 리스너
     */
    static EqpLifecycleOutcomeListener noOp() {
        return outcome -> {
            // no-op
        };
    }
}


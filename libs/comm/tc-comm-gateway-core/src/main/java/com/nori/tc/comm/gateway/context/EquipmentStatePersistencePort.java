package com.nori.tc.comm.gateway.context;

/**
 * 설비 상태/이력 영속화 포트입니다.
 *
 * <p>기본 구현은 DB(tc_eqp_state, tc_eqp_state_hist)를 사용하지만,
 * 코어/어댑터 분리를 위해 인터페이스로 노출합니다.</p>
 */
public interface EquipmentStatePersistencePort {

    /**
     * CREATE/UPDATE 요청 처리 이력을 기록합니다.
     */
    void recordCreateOrUpdate(String eqpId, String traceId, String eventType, String detailMessage);

    /**
     * START 요청 처리 결과를 상태/이력에 반영합니다.
     */
    void recordStart(String eqpId, String traceId, String detailMessage);

    /**
     * END 요청 처리 결과를 상태/이력에 반영합니다.
     */
    void recordEnd(String eqpId, String traceId, String detailMessage);

    /**
     * DELETE 요청 처리 결과를 상태/이력에 반영합니다.
     */
    void recordDelete(String eqpId, String traceId, String detailMessage);

    /**
     * 상태 영속화를 사용하지 않는 환경을 위한 NO-OP 구현입니다.
     */
    EquipmentStatePersistencePort NO_OP = new EquipmentStatePersistencePort() {
        @Override
        public void recordCreateOrUpdate(
                final String eqpId,
                final String traceId,
                final String eventType,
                final String detailMessage
        ) {
            // no-op
        }

        @Override
        public void recordStart(final String eqpId, final String traceId, final String detailMessage) {
            // no-op
        }

        @Override
        public void recordEnd(final String eqpId, final String traceId, final String detailMessage) {
            // no-op
        }

        @Override
        public void recordDelete(final String eqpId, final String traceId, final String detailMessage) {
            // no-op
        }
    };
}


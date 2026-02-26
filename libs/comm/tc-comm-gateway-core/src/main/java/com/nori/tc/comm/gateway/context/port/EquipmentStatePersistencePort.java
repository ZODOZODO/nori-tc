package com.nori.tc.comm.gateway.context.port;

/**
 * 설비 상태/이력 영속화 포트입니다.
 *
 * <p>기본 구현은 DB(tc_eqp_state, tc_eqp_state_hist)를 사용하지만,
 * 코어/어댑터 분리를 위해 인터페이스로 노출합니다.</p>
 */
public interface EquipmentStatePersistencePort {

    /**
     * CREATE/UPDATE 요청 처리 이력을 커맨드 기반 입력으로 기록합니다.
     *
     * <p>신규 호출부는 가능하면 이 메서드를 사용하여 eqpKey를 함께 전달해야 합니다.
     * 기본 구현은 하위 호환을 위해 기존 문자열 기반 메서드로 위임합니다.</p>
     *
     * @param command 상태/이력 영속화 입력 커맨드
     */
    default void recordCreateOrUpdate(final EquipmentStatePersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        recordCreateOrUpdate(
                command.eqpId(),
                command.traceId(),
                command.eventType(),
                command.detailMessage()
        );
    }

    /**
     * CREATE/UPDATE 요청 처리 이력을 기록합니다.
     */
    void recordCreateOrUpdate(String eqpId, String traceId, String eventType, String detailMessage);

    /**
     * START 요청 처리 결과를 커맨드 기반 입력으로 상태/이력에 반영합니다.
     *
     * <p>기본 구현은 기존 문자열 기반 메서드로 위임합니다.</p>
     *
     * @param command 상태/이력 영속화 입력 커맨드
     */
    default void recordStart(final EquipmentStatePersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        recordStart(command.eqpId(), command.traceId(), command.detailMessage());
    }

    /**
     * START 요청 처리 결과를 상태/이력에 반영합니다.
     */
    void recordStart(String eqpId, String traceId, String detailMessage);

    /**
     * END 요청 처리 결과를 커맨드 기반 입력으로 상태/이력에 반영합니다.
     *
     * <p>기본 구현은 기존 문자열 기반 메서드로 위임합니다.</p>
     *
     * @param command 상태/이력 영속화 입력 커맨드
     */
    default void recordEnd(final EquipmentStatePersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        recordEnd(command.eqpId(), command.traceId(), command.detailMessage());
    }

    /**
     * END 요청 처리 결과를 상태/이력에 반영합니다.
     */
    void recordEnd(String eqpId, String traceId, String detailMessage);

    /**
     * DELETE 요청 처리 결과를 커맨드 기반 입력으로 상태/이력에 반영합니다.
     *
     * <p>기본 구현은 기존 문자열 기반 메서드로 위임합니다.</p>
     *
     * @param command 상태/이력 영속화 입력 커맨드
     */
    default void recordDelete(final EquipmentStatePersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        recordDelete(command.eqpId(), command.traceId(), command.detailMessage());
    }

    /**
     * DELETE 요청 처리 결과를 상태/이력에 반영합니다.
     */
    void recordDelete(String eqpId, String traceId, String detailMessage);

    /**
     * 상태 영속화를 사용하지 않는 환경을 위한 NO-OP 구현입니다.
     *
     * <p>테스트/로컬 개발/일부 경량 배포 환경에서 영속화 어댑터를 연결하지 않아도
     * 코어 로직이 동일한 인터페이스로 동작할 수 있도록 제공합니다.</p>
     */
    EquipmentStatePersistencePort NO_OP = new EquipmentStatePersistencePort() {
        @Override
        public void recordCreateOrUpdate(
                final String eqpId,
                final String traceId,
                final String eventType,
                final String detailMessage
        ) {
            // 영속화 기능을 비활성화한 환경에서는 CREATE/UPDATE 상태 이력을 저장하지 않습니다.
            // 의도적인 no-op 이므로 로그를 남기지 않아 대량 요청 시 로그 노이즈를 방지합니다.
            // no-op
        }

        @Override
        public void recordStart(final String eqpId, final String traceId, final String detailMessage) {
            // 영속화 기능을 비활성화한 환경에서는 START 이력 기록을 수행하지 않습니다.
            // 운영 로그는 상위 계층에서 이미 남기므로 여기서는 추가 로그를 남기지 않습니다.
            // no-op
        }

        @Override
        public void recordEnd(final String eqpId, final String traceId, final String detailMessage) {
            // 영속화 기능을 비활성화한 환경에서는 END 이력 기록을 수행하지 않습니다.
            // NO_OP 포트는 호출 계약만 만족하고 부작용은 만들지 않습니다.
            // no-op
        }

        @Override
        public void recordDelete(final String eqpId, final String traceId, final String detailMessage) {
            // 영속화 기능을 비활성화한 환경에서는 DELETE 이력 기록을 수행하지 않습니다.
            // 삭제 요청도 동일하게 조용히 무시해 호출부 분기(if != null)를 제거합니다.
            // no-op
        }
    };
}

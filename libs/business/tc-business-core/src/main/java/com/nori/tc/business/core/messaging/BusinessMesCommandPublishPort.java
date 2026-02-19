package com.nori.tc.business.core.messaging;

/**
 * Business Core -> 외부 MES 어댑터 명령 발행 포트입니다.
 */
@FunctionalInterface
public interface BusinessMesCommandPublishPort {

    /**
     * MES 명령 메시지를 발행합니다.
     *
     * @param command 발행할 명령
     * @throws Exception 발행 실패
     */
    void publish(BusinessMesCommandMessage command) throws Exception;

    /**
     * 기본 no-op 구현을 반환합니다.
     *
     * <p>어댑터가 없는 테스트/초기 단계에서도 코어가 동작하도록 사용합니다.</p>
     *
     * @return no-op 포트
     */
    static BusinessMesCommandPublishPort noop() {
        return ignored -> {
            // no-op
        };
    }
}

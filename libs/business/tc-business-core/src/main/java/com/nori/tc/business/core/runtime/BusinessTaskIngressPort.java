package com.nori.tc.business.core.runtime;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;

/**
 * 비즈니스 코어 런타임으로 메시지를 주입하는 인바운드 포트입니다.
 *
 * <p>어댑터(Kafka Listener 등)는 이 포트를 통해 코어 런타임에 메시지를 전달합니다.</p>
 * <p>포트의 반환값은 코어가 메시지를 큐에 수락했는지 여부를 의미합니다.</p>
 */
@FunctionalInterface
public interface BusinessTaskIngressPort {

    /**
     * 코어 런타임 큐에 인바운드 레코드를 제출합니다.
     *
     * @param record 전달할 인바운드 레코드(필수)
     * @return 수락 성공 시 true, 큐 포화/중단 상태 등으로 거부되면 false
     */
    boolean submit(BusinessInboundRecord record);
}

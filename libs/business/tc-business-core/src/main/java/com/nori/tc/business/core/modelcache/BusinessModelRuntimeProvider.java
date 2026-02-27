package com.nori.tc.business.core.modelcache;

import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;

import java.util.Optional;

/**
 * Business model runtime 조회 포트입니다.
 *
 * <p>업무 처리 hot path에서는 DB가 아니라 이 포트를 통해 메모리 스냅샷을 조회합니다.</p>
 */
public interface BusinessModelRuntimeProvider {

    /**
     * 현재 시점의 불변 스냅샷을 반환합니다.
     *
     * @return 현재 스냅샷
     */
    BusinessModelRuntimeSnapshot currentSnapshot();

    /**
     * eqpId에 매핑된 modelVersionKey를 조회합니다.
     *
     * @param eqpId 장비 ID
     * @return modelVersionKey(optional)
     */
    default Optional<Long> findModelVersionKeyByEqpId(final String eqpId) {
        return currentSnapshot().findModelVersionKeyByEqpId(eqpId);
    }

    /**
     * eqpId에 매핑된 model runtime을 조회합니다.
     *
     * @param eqpId 장비 ID
     * @return model runtime(optional)
     */
    default Optional<TcModelRuntime> findRuntimeByEqpId(final String eqpId) {
        return currentSnapshot().findRuntimeByEqpId(eqpId);
    }

    /**
     * modelVersionKey로 model runtime을 조회합니다.
     *
     * @param modelVersionKey model key
     * @return model runtime(optional)
     */
    default Optional<TcModelRuntime> findRuntimeByModelVersionKey(final long modelVersionKey) {
        return currentSnapshot().findRuntimeByModelVersionKey(modelVersionKey);
    }

    /**
     * 런타임 주입 이전 단위 테스트/골격 단계에서 사용할 no-op provider를 반환합니다.
     *
     * @return 항상 빈 스냅샷을 반환하는 provider
     */
    static BusinessModelRuntimeProvider noop() {
        return NoopHolder.INSTANCE;
    }

    /**
     * 지연 초기화 holder입니다.
     */
    final class NoopHolder {
        private static final BusinessModelRuntimeProvider INSTANCE = BusinessModelRuntimeSnapshot::empty;

        /**
         * NoopHolder 생성자를 초기화합니다.
         *
         */

        private NoopHolder() {
            throw new IllegalStateException("NoopHolder cannot be instantiated");
        }
    }
}


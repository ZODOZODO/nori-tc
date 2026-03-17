package com.nori.tc.business.core.modelcache;

/**
 * Business model/eqp 파라미터 캐시 변경 포트입니다.
 *
 * <p>
 * DB에서 파라미터가 변경되는 시점에 이 포트를 통해 메모리 캐시를 갱신합니다.
 * </p>
 */
public interface BusinessModelParamMutationPort {

    /**
     * 특정 modelVersionKey의 파라미터 캐시를 DB에서 다시 적재합니다.
     *
     * @param modelVersionKey 갱신할 모델 버전 키
     */
    void reloadModelParams(long modelVersionKey);

    /**
     * 특정 eqpKey의 파라미터 캐시를 DB에서 다시 적재합니다.
     *
     * @param eqpKey 갱신할 설비 DB PK
     */
    void reloadEqpParams(long eqpKey);
}

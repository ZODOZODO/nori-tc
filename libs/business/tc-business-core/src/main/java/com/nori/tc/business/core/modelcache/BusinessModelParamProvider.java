package com.nori.tc.business.core.modelcache;

import com.nori.tc.business.domain.modelcache.BusinessModelParamSnapshot;

import java.util.Map;
import java.util.Optional;

/**
 * Business model/eqp 파라미터 조회 포트입니다.
 *
 * <p>
 * 파라미터 우선순위: EQP 파라미터 > Model 파라미터.
 * 업무 처리 hot path에서는 DB가 아니라 이 포트를 통해 메모리 스냅샷을 조회합니다.
 * </p>
 */
public interface BusinessModelParamProvider {

    /**
     * 현재 시점의 불변 파라미터 스냅샷을 반환합니다.
     *
     * @return 현재 파라미터 스냅샷
     */
    BusinessModelParamSnapshot currentParamSnapshot();

    /**
     * eqpId와 paramName으로 파라미터 값을 조회합니다.
     *
     * <p>EQP 파라미터가 존재하면 EQP 값을, 없으면 Model 파라미터 값을 반환합니다.</p>
     *
     * @param eqpId     장비 ID
     * @param paramName 파라미터 이름
     * @return 파라미터 값(optional)
     */
    Optional<String> findParam(String eqpId, String paramName);

    /**
     * eqpId 기준으로 EQP + Model 파라미터를 병합하여 반환합니다.
     *
     * <p>동일한 paramName이 양쪽에 모두 존재할 경우 EQP 값이 사용됩니다.</p>
     *
     * @param eqpId 장비 ID
     * @return 병합된 파라미터 맵 (불변)
     */
    Map<String, String> findAllParams(String eqpId);

    /**
     * 특정 modelVersionKey의 Model 파라미터 맵을 반환합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 파라미터 맵 (불변)
     */
    Map<String, String> findModelParams(long modelVersionKey);

    /**
     * 특정 eqpId의 EQP 파라미터 맵을 반환합니다.
     *
     * @param eqpId 장비 ID
     * @return 파라미터 맵 (불변)
     */
    Map<String, String> findEqpParams(String eqpId);
}

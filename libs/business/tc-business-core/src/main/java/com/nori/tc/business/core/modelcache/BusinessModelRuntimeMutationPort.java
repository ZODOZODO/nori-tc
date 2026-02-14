package com.nori.tc.business.core.modelcache;

/**
 * model runtime 캐시 조회/갱신 포트입니다.
 *
 * <p>UI 이벤트 처리 경로에서 model runtime 캐시를 갱신할 때
 * 구현체 세부사항을 감추기 위해 사용하는 계약입니다.</p>
 */
public interface BusinessModelRuntimeMutationPort extends BusinessModelRuntimeProvider {

    /**
     * 전체 스냅샷을 다시 로딩합니다.
     */
    void reloadAll();

    /**
     * 특정 modelKey runtime을 재조립합니다.
     *
     * @param modelKey model key
     */
    void reloadModelRuntime(long modelKey);

    /**
     * eqpId -> modelKey 바인딩을 갱신합니다.
     *
     * @param eqpId 장비 ID
     * @param modelKey model key
     */
    void updateEqpBinding(String eqpId, long modelKey);
}


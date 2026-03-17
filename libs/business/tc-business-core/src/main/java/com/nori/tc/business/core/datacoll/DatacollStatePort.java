package com.nori.tc.business.core.datacoll;

import com.nori.tc.business.domain.datacoll.DatacollState;

import java.util.Optional;

/**
 * DATACOLL 수집 상태 저장소 포트입니다.
 *
 * <p>코어는 "DatacollState를 언제 저장/조회/갱신/삭제해야 하는지"만 책임지고,
 * 실제 저장 위치(Redis 등)는 어댑터가 책임집니다.</p>
 *
 * <p>Key 구조: {@code (eqpId, correlationId)} 조합으로 lot 단위 수집 상태를 식별합니다.</p>
 *
 * <ul>
 *   <li>저장: DCSPECREQ_REP 수신 시 초기 상태 저장</li>
 *   <li>조회: COLLECT_DCDATA / DATACOLL action 실행 시</li>
 *   <li>갱신: COLLECT_DCDATA action 실행 후 collectionState 갱신 시</li>
 *   <li>삭제: DATACOLL 보고 완료 후 Redis key 즉시 삭제</li>
 * </ul>
 */
public interface DatacollStatePort {

    /**
     * DatacollState를 저장합니다.
     *
     * <p>DCSPECREQ_REP 수신 시 초기 상태를 저장할 때 호출합니다.
     * 동일 key가 이미 존재하면 덮어씁니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @param state         저장할 DatacollState
     */
    void save(String eqpId, String correlationId, DatacollState state);

    /**
     * DatacollState를 조회합니다.
     *
     * <p>key가 존재하지 않으면 {@link Optional#empty()}를 반환합니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @return 조회된 DatacollState. 없으면 Optional.empty()
     */
    Optional<DatacollState> findByKey(String eqpId, String correlationId);

    /**
     * DatacollState를 갱신합니다.
     *
     * <p>COLLECT_DCDATA action 실행 후 collectionState를 갱신할 때 호출합니다.
     * 내부적으로 save와 동일하게 덮어쓰기(overwrite)로 처리하며, TTL도 갱신됩니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     * @param state         갱신할 DatacollState
     */
    void update(String eqpId, String correlationId, DatacollState state);

    /**
     * DatacollState를 즉시 삭제합니다.
     *
     * <p>DATACOLL 보고 완료 후 Redis key를 삭제할 때 호출합니다.
     * 삭제 실패 시에도 TTL이 자동으로 만료되어 최종 정리됩니다.</p>
     *
     * @param eqpId         장비 ID
     * @param correlationId lot 단위 식별자
     */
    void delete(String eqpId, String correlationId);
}

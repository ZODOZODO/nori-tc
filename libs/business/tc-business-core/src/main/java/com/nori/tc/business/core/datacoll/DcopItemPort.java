package com.nori.tc.business.core.datacoll;

import com.nori.tc.db.domain.model.TcModelDcopItem;

import java.util.List;

/**
 * DCOP Item 조회 포트 인터페이스입니다.
 *
 * <p>코어는 "어떤 DCOP Item이 필요한지"만 책임지고,
 * 실제 저장소(DB 등) 조회는 어댑터가 책임집니다.</p>
 *
 * <p>조회 결과 정렬 기준: {@code orderRule ASC, dcopItemName ASC}</p>
 *
 * <ul>
 *   <li>조회 시점: {@code COLLECT_DCDATA} / {@code DATACOLL} action 실행 시</li>
 * </ul>
 */
public interface DcopItemPort {

    /**
     * 모델 버전 키 기준 DCOP Item 전체 목록을 조회합니다.
     *
     * <p>정렬 기준: {@code orderRule ASC, dcopItemName ASC}</p>
     *
     * @param modelVersionKey 조회할 모델 버전 키
     * @return DCOP Item 목록. 없으면 빈 List
     */
    List<TcModelDcopItem> findAllByModelVersionKey(long modelVersionKey);
}

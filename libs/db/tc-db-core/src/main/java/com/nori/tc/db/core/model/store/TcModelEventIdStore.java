package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;

/**
 * tc_model_eventid CRUD 인터페이스.
 *
 * - (model_version_key, event_id) 유니크 키를 기준으로 upsert합니다.
 */
public interface TcModelEventIdStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcModelEventId upsert(UpsertTcModelEventId command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelEventId> findByEventKey(long eventKey);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param eventId 처리할 이벤트 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcModelEventId> findByModelVersionKeyAndEventId(long modelVersionKey, String eventId);

    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcModelEventId> findAllByModelVersionKey(long modelVersionKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     */
    void deleteByEventKey(long eventKey);
}

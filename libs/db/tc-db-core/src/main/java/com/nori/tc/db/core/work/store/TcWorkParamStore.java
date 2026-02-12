package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkParam;
import com.nori.tc.db.domain.work.TcWorkParam;

/**
 * tc_work_param CRUD 인터페이스.
 *
 * <p>
 * - Unique(work_key, param_name)을 기준으로 upsert를 수행한다.
 * - 변경 대상은 param_value만 허용한다.
 * - 조회는 DB 페이징을 강제한다.
 * </p>
 */
public interface TcWorkParamStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcWorkParam upsert(UpsertTcWorkParam command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param paramName DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkParam> findByWorkKeyAndName(long workKey, String paramName);

    /**
     * 특정 작업(work_key)의 파라미터 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWorkParam> findAllByWorkKey(long workKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workParamKey 대상 키 값
     */
    void deleteByWorkParamKey(long workParamKey);
}

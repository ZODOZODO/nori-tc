package com.nori.tc.db.core.user.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.user.upsert.UpsertTcUiAuthSession;
import com.nori.tc.db.domain.user.TcUiAuthSession;

/**
 * tc_ui_auth_session CRUD 인터페이스.
 *
 * <p>
 * 목표:
 * - UI 인증 세션의 기본 CRUD를 단일 Port로 제공한다.
 * - App 계층은 이 인터페이스만 알고 세션 저장/조회/삭제를 수행한다.
 * </p>
 *
 * <p>
 * 제공 기능:
 * - upsert: 토큰 기준으로 저장(있으면 갱신, 없으면 생성)
 * - findByToken: 단건 조회
 * - findAllByUserPk: 사용자별 세션 목록 페이징 조회
 * - deleteByToken: 토큰 기준 삭제
 * </p>
 */
public interface TcUiAuthSessionStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcUiAuthSession upsert(UpsertTcUiAuthSession command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param token DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUiAuthSession> findByToken(String token);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param userPk DB Core 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcUiAuthSession> findAllByUserPk(long userPk, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param token DB Core 계층 처리에 사용하는 입력 값
     */
    void deleteByToken(String token);
}

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

    TcUiAuthSession upsert(UpsertTcUiAuthSession command);

    Optional<TcUiAuthSession> findByToken(String token);

    List<TcUiAuthSession> findAllByUserPk(long userPk, PageRequest page);

    void deleteByToken(String token);
}

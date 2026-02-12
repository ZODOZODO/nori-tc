package com.nori.tc.db.mybatis.common.store.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.user.store.TcUiAuthSessionStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUiAuthSession;
import com.nori.tc.db.domain.user.TcUiAuthSession;
import com.nori.tc.db.mybatis.common.mapper.user.TcUiAuthSessionMapper;

/**
 * tc_ui_auth_session MyBatis Store 구현체.
 *
 * <p>
 * 정책:
 * - 토큰 기준 update-first upsert 전략을 사용한다.
 * - issuedAt/ revoked 등 기본값은 Java에서 보정하여 DB 기본값과 동일한 의미로 맞춘다.
 * </p>
 */
@Repository
public class TcUiAuthSessionMybatisStore implements TcUiAuthSessionStore {

    private final TcUiAuthSessionMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcUiAuthSessionMybatisStore(TcUiAuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    @Transactional
    public TcUiAuthSession upsert(UpsertTcUiAuthSession command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        UpsertTcUiAuthSession normalized = normalizeCommand(command);
        validateCommand(normalized);

        final String token = normalized.token();
        final long userPk = normalized.userPk();

        final TcUiAuthSession row = new TcUiAuthSession(
                token,
                userPk,
                normalized.issuedAt(),
                normalized.expiresAt(),
                normalized.lastSeenAt(),
                normalized.revoked()
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.update(row);
                }
            }

            return mapper.findByToken(token)
                    .orElseThrow(() -> new DbAccessException("tc_ui_auth_session upsert succeeded but row not found. token=" + token));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_ui_auth_session upsert duplicate key. token=" + token, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_auth_session upsert failed. token=" + token, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_auth_session upsert failed (unexpected). token=" + token, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param token DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiAuthSession> findByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        try {
            return mapper.findByToken(token);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_auth_session findByToken failed. token=" + token, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_auth_session findByToken failed (unexpected). token=" + token, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUiAuthSession> findAllByUserPk(long userPk, PageRequest page) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByUserPk(userPk, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_auth_session findAllByUserPk failed. userPk=" + userPk, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_auth_session findAllByUserPk failed (unexpected). userPk=" + userPk, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param token DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByToken(String token) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        try {
            mapper.deleteByToken(token);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_auth_session deleteByToken failed. token=" + token, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_auth_session deleteByToken failed (unexpected). token=" + token, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcUiAuthSession command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("command.token must not be null/blank");
        }
        if (command.token().length() > 64) {
            throw new IllegalArgumentException("command.token length must be <= 64");
        }
        if (command.userPk() == null || command.userPk() <= 0) {
            throw new IllegalArgumentException("command.userPk must be positive");
        }
        if (command.issuedAt() == null) {
            throw new IllegalArgumentException("command.issuedAt must not be null");
        }
        if (command.expiresAt() == null) {
            throw new IllegalArgumentException("command.expiresAt must not be null");
        }
        if (command.revoked() == null) {
            throw new IllegalArgumentException("command.revoked must not be null");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private UpsertTcUiAuthSession normalizeCommand(UpsertTcUiAuthSession command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcUiAuthSession(
                command.token(),
                command.userPk(),
                command.issuedAt() == null ? OffsetDateTime.now() : command.issuedAt(),
                command.expiresAt(),
                command.lastSeenAt(),
                command.revoked() == null ? Boolean.FALSE : command.revoked()
        );
    }
}

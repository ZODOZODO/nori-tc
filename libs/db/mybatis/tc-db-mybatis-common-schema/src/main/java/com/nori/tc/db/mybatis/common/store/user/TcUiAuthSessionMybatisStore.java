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

    public TcUiAuthSessionMybatisStore(TcUiAuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUiAuthSession upsert(UpsertTcUiAuthSession command) {
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

    @Override
    @Transactional
    public void deleteByToken(String token) {
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

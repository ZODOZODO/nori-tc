package com.nori.tc.db.mybatis.common.store.user;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.user.store.TcUiPermissionStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUiPermission;
import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.user.TcUiPermission;
import com.nori.tc.db.mybatis.common.mapper.user.TcUiPermissionMapper;

/**
 * tc_ui_permission MyBatis Store 구현.
 *
 * <p>
 * - insert 후 perm_id를 반환하지 않으므로 perm_code로 재조회한다.
 * - match_type의 기본값(PREFIX)을 Java 레벨에서 보정하여 명시적으로 저장한다.
 * - created_by/updated_by는 null이면 SYSTEM으로 보정한다.
 * </p>
 */
@Repository
public class TcUiPermissionMybatisStore implements TcUiPermissionStore {

    private static final String DEFAULT_ACTOR = "SYSTEM";

    private final TcUiPermissionMapper mapper;

    public TcUiPermissionMybatisStore(TcUiPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUiPermission upsert(UpsertTcUiPermission command) {
        if (command == null) throw new IllegalArgumentException("UpsertTcUiPermission must not be null");

        TcUiPermission row = toRow(command);

        try {
            if (row.permId() > 0) {
                mapper.update(row);
                return mapper.findByPermId(row.permId())
                        .orElseThrow(() -> new DbAccessException("tc_ui_permission upsert failed: cannot re-fetch by perm_id"));
            }

            mapper.insert(row);
            return mapper.findByPermCode(row.permCode())
                    .orElseThrow(() -> new DbAccessException("tc_ui_permission upsert failed: cannot re-fetch by perm_code"));

        } catch (DataAccessException e) {
            throw new DbDuplicateKeyException("tc_ui_permission upsert failed. permCode=" + command.permCode(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission upsert failed (unexpected).", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiPermission> findByPermId(long permId) {
        try {
            return mapper.findByPermId(permId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_permission findByPermId failed. permId=" + permId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission findByPermId failed (unexpected). permId=" + permId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiPermission> findByPermCode(String permCode) {
        try {
            return mapper.findByPermCode(permCode);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_permission findByPermCode failed. permCode=" + permCode, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission findByPermCode failed (unexpected). permCode=" + permCode, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUiPermission> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_permission findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission findAll failed (unexpected).", e);
        }
    }

    @Override
    @Transactional
    public void deleteByPermId(long permId) {
        try {
            // 삭제는 멱등으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByPermId(permId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_permission deleteByPermId failed. permId=" + permId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission deleteByPermId failed (unexpected). permId=" + permId, e);
        }
    }

    private TcUiPermission toRow(UpsertTcUiPermission command) {
        long permId = command.permId() == null ? 0L : command.permId();
        TcUiPermissionMatchType matchType = command.matchType() == null
                ? TcUiPermissionMatchType.PREFIX
                : command.matchType();
        String createdBy = command.createdBy() == null ? DEFAULT_ACTOR : command.createdBy();
        String updatedBy = command.updatedBy() == null ? DEFAULT_ACTOR : command.updatedBy();

        return new TcUiPermission(
                permId,
                command.permCode(),
                command.permName(),
                command.resourceType(),
                matchType,
                command.resource(),
                command.httpMethod(),
                command.description(),
                command.isActive(),
                null,
                null,
                createdBy,
                updatedBy
        );
    }
}

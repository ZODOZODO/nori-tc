package com.nori.tc.db.mybatis.common.store.user;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.user.store.TcUserGroupPermissionStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupPermission;
import com.nori.tc.db.domain.user.TcUserGroupPermission;
import com.nori.tc.db.mybatis.common.mapper.user.TcUserGroupPermissionMapper;

/**
 * tc_user_group_permission MyBatis Store 구현체.
 *
 * - Unique: (group_id, perm_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 */
@Repository
public class TcUserGroupPermissionMybatisStore implements TcUserGroupPermissionStore {

    private final TcUserGroupPermissionMapper mapper;

    public TcUserGroupPermissionMybatisStore(TcUserGroupPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserGroupPermission upsert(UpsertTcUserGroupPermission command) {
        validateCommand(command);

        final long groupId = command.groupId();
        final long permId = command.permId();

        final TcUserGroupPermission row = new TcUserGroupPermission(
                0L,
                groupId,
                permId,
                command.grantedAt(), // SQL에서는 CURRENT_TIMESTAMP로 갱신(입력값은 참고용)
                command.grantedBy()
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

            return mapper.findByGroupIdPermId(groupId, permId)
                    .orElseThrow(() -> new DbAccessException("tc_user_group_permission upsert succeeded but row not found. groupId/permId=" + groupId + "/" + permId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_user_group_permission upsert duplicate key. groupId/permId=" + groupId + "/" + permId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_permission upsert failed. groupId/permId=" + groupId + "/" + permId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_permission upsert failed (unexpected). groupId/permId=" + groupId + "/" + permId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroupPermission> findByGroupIdPermId(long groupId, long permId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
        if (permId <= 0) {
            throw new IllegalArgumentException("permId must be > 0");
        }
        try {
            return mapper.findByGroupIdPermId(groupId, permId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_permission findByGroupIdPermId failed. groupId/permId=" + groupId + "/" + permId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_permission findByGroupIdPermId failed (unexpected). groupId/permId=" + groupId + "/" + permId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupPermission> findAllByGroupId(long groupId, PageRequest page) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByGroupId(groupId, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_permission findAllByGroupId failed. groupId=" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_permission findAllByGroupId failed (unexpected). groupId=" + groupId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByGroupIdPermId(long groupId, long permId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
        if (permId <= 0) {
            throw new IllegalArgumentException("permId must be > 0");
        }
        try {
            mapper.deleteByGroupIdPermId(groupId, permId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_permission deleteByGroupIdPermId failed. groupId/permId=" + groupId + "/" + permId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_permission deleteByGroupIdPermId failed (unexpected). groupId/permId=" + groupId + "/" + permId, e);
        }
    }

    private void validateCommand(UpsertTcUserGroupPermission command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.groupId() <= 0) throw new IllegalArgumentException("command.groupId must be > 0");
        if (command.permId() <= 0) throw new IllegalArgumentException("command.permId must be > 0");
    }
}

package com.nori.tc.db.mybatis.common.store.user;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.user.store.TcUserGroupStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroup;
import com.nori.tc.db.domain.user.TcUserGroup;
import com.nori.tc.db.mybatis.common.mapper.user.TcUserGroupMapper;

/**
 * tc_user_group MyBatis Store 구현체.
 */
@Repository
public class TcUserGroupMybatisStore implements TcUserGroupStore {

    private final TcUserGroupMapper mapper;

    public TcUserGroupMybatisStore(TcUserGroupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserGroup upsert(UpsertTcUserGroup command) {
        if (command == null) throw new IllegalArgumentException("UpsertTcUserGroup must not be null");
        if (command.groupCode() == null || command.groupCode().isBlank()) {
            throw new IllegalArgumentException("groupCode must not be null/blank");
        }
        if (command.groupName() == null || command.groupName().isBlank()) {
            throw new IllegalArgumentException("groupName must not be null/blank");
        }

        try {
            TcUserGroup row = toRow(command, resolveGroupId(command));
            if (row.groupId() > 0) {
                mapper.update(row);
            } else {
                mapper.insert(row);
            }
            return mapper.findByGroupCode(command.groupCode())
                    .orElseThrow(() -> new DbAccessException("tc_user_group upsert failed: cannot re-fetch by groupCode"));

        } catch (DataAccessException e) {
            throw new DbDuplicateKeyException("tc_user_group upsert failed. groupCode=" + command.groupCode(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group upsert failed (unexpected).", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroup> findByGroupId(long groupId) {
        try {
            return mapper.findByGroupId(groupId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group findByGroupId failed. groupId=" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group findByGroupId failed (unexpected). groupId=" + groupId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroup> findByGroupCode(String groupCode) {
        try {
            return mapper.findByGroupCode(groupCode);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group findByGroupCode failed. groupCode=" + groupCode, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group findByGroupCode failed (unexpected). groupCode=" + groupCode, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroup> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group findAll failed (unexpected).", e);
        }
    }

    @Override
    @Transactional
    public void deleteByGroupId(long groupId) {
        try {
            mapper.deleteByGroupId(groupId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group deleteByGroupId failed. groupId=" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group deleteByGroupId failed (unexpected). groupId=" + groupId, e);
        }
    }

    private long resolveGroupId(UpsertTcUserGroup command) {
        Long groupId = command.groupId();
        if (groupId != null && groupId > 0) {
            return groupId;
        }
        return mapper.findByGroupCode(command.groupCode()).map(TcUserGroup::groupId).orElse(0L);
    }

    private TcUserGroup toRow(UpsertTcUserGroup command, long groupId) {
        return new TcUserGroup(
                groupId,
                command.groupCode(),
                command.groupName(),
                command.description(),
                command.isActive(),
                null,
                null
        );
    }
}

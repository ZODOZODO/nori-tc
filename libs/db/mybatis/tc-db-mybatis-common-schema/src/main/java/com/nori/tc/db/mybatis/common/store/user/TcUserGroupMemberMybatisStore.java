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
import com.nori.tc.db.core.user.store.TcUserGroupMemberStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupMember;
import com.nori.tc.db.domain.user.TcUserGroupMember;
import com.nori.tc.db.mybatis.common.mapper.user.TcUserGroupMemberMapper;

/**
 * tc_user_group_member MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (user_pk, group_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcUserGroupMemberMybatisStore implements TcUserGroupMemberStore {

    private final TcUserGroupMemberMapper mapper;

    public TcUserGroupMemberMybatisStore(TcUserGroupMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserGroupMember upsert(UpsertTcUserGroupMember command) {
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcUserGroupMember row = new TcUserGroupMember(
                resolvedKey,
                command.userPk(),
                command.groupId(),
                command.grantedAt(),
                command.grantedBy()
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_user_group_member insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByUserPkAndGroupId(command.userPk(), command.groupId())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_user_group_member upsert succeeded but row not found. key=" + command.userPk() + "/" + command.groupId()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_user_group_member upsert duplicate (user_pk, group_id). key=" + command.userPk() + "/" + command.groupId(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_user_group_member upsert failed. key=" + command.userPk() + "/" + command.groupId(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_user_group_member upsert failed (unexpected). key=" + command.userPk() + "/" + command.groupId(),
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroupMember> findByUgmKey(long ugmKey) {
        if (ugmKey <= 0) {
            throw new IllegalArgumentException("ugmKey must be > 0");
        }
        try {
            return mapper.findByUgmKey(ugmKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_member findByUgmKey failed. ugmKey=" + ugmKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_member findByUgmKey failed (unexpected). ugmKey=" + ugmKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroupMember> findByUserPkAndGroupId(long userPk, long groupId) {
        validateUserPk(userPk);
        validateGroupId(groupId);
        try {
            return mapper.findByUserPkAndGroupId(userPk, groupId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_member findByUserPkAndGroupId failed. key=" + userPk + "/" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_member findByUserPkAndGroupId failed (unexpected). key=" + userPk + "/" + groupId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupMember> findAllByUserPk(long userPk, PageRequest page) {
        validateUserPk(userPk);
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByUserPk(userPk, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_member findAllByUserPk failed. userPk=" + userPk, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_member findAllByUserPk failed (unexpected). userPk=" + userPk, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupMember> findAllByGroupId(long groupId, PageRequest page) {
        validateGroupId(groupId);
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByGroupId(groupId, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_member findAllByGroupId failed. groupId=" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_member findAllByGroupId failed (unexpected). groupId=" + groupId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByUgmKey(long ugmKey) {
        if (ugmKey <= 0) {
            throw new IllegalArgumentException("ugmKey must be > 0");
        }
        try {
            mapper.deleteByUgmKey(ugmKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_member deleteByUgmKey failed. ugmKey=" + ugmKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_member deleteByUgmKey failed (unexpected). ugmKey=" + ugmKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByUserPkAndGroupId(long userPk, long groupId) {
        validateUserPk(userPk);
        validateGroupId(groupId);
        try {
            mapper.deleteByUserPkAndGroupId(userPk, groupId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group_member deleteByUserPkAndGroupId failed. key=" + userPk + "/" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group_member deleteByUserPkAndGroupId failed (unexpected). key=" + userPk + "/" + groupId, e);
        }
    }

    private void validateCommand(UpsertTcUserGroupMember command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.ugmKey() != null && command.ugmKey() <= 0) {
            throw new IllegalArgumentException("command.ugmKey must be > 0 when provided");
        }
        validateUserPk(command.userPk());
        validateGroupId(command.groupId());
    }

    private void validateUserPk(long userPk) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be > 0");
        }
    }

    private void validateGroupId(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
    }

    private long resolveKey(UpsertTcUserGroupMember command) {
        if (command.ugmKey() != null) {
            return command.ugmKey();
        }

        return mapper.findByUserPkAndGroupId(command.userPk(), command.groupId())
                .map(TcUserGroupMember::ugmKey)
                .orElse(0L);
    }
}

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

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcUserGroupMemberMybatisStore(TcUserGroupMemberMapper mapper) {
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
    public TcUserGroupMember upsert(UpsertTcUserGroupMember command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param ugmKey 대상 키 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param ugmKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByUgmKey(long ugmKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByUserPkAndGroupId(long userPk, long groupId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
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

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     */
    private void validateUserPk(long userPk) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be > 0");
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     */
    private void validateGroupId(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(UpsertTcUserGroupMember command) {
        if (command.ugmKey() != null) {
            return command.ugmKey();
        }

        return mapper.findByUserPkAndGroupId(command.userPk(), command.groupId())
                .map(TcUserGroupMember::ugmKey)
                .orElse(0L);
    }
}

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

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcUserGroupMybatisStore(TcUserGroupMapper mapper) {
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
    public TcUserGroup upsert(UpsertTcUserGroup command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupCode DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByGroupId(long groupId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            mapper.deleteByGroupId(groupId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_user_group deleteByGroupId failed. groupId=" + groupId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_user_group deleteByGroupId failed (unexpected). groupId=" + groupId, e);
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveGroupId(UpsertTcUserGroup command) {
        Long groupId = command.groupId();
        if (groupId != null && groupId > 0) {
            return groupId;
        }
        return mapper.findByGroupCode(command.groupCode()).map(TcUserGroup::groupId).orElse(0L);
    }

    
    /**
     * DB MyBatis 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
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

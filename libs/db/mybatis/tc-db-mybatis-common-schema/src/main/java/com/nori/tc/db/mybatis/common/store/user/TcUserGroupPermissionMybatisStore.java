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

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcUserGroupPermissionMybatisStore(TcUserGroupPermissionMapper mapper) {
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
    public TcUserGroupPermission upsert(UpsertTcUserGroupPermission command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByGroupIdPermId(long groupId, long permId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcUserGroupPermission command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.groupId() <= 0) throw new IllegalArgumentException("command.groupId must be > 0");
        if (command.permId() <= 0) throw new IllegalArgumentException("command.permId must be > 0");
    }
}

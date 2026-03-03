package com.nori.tc.db.mybatis.common.store.user;

import java.util.Collection;
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

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcUiPermissionMybatisStore(TcUiPermissionMapper mapper) {
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
    public TcUiPermission upsert(UpsertTcUiPermission command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permCode DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * perm_id 목록 기준 활성 권한 전체 조회 (IN 절 + is_active=true, 페이징 없음).
     *
     * @param permIds 조회할 perm_id 컬렉션
     * @return is_active=true인 권한 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUiPermission> findAllActiveByPermIdIn(Collection<Long> permIds) {
        if (permIds == null || permIds.isEmpty()) {
            return List.of();
        }
        try {
            return mapper.findAllActiveByPermIdIn(permIds);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_permission findAllActiveByPermIdIn failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission findAllActiveByPermIdIn failed (unexpected).", e);
        }
    }

    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByPermId(long permId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            // 삭제는 멱등으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByPermId(permId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_ui_permission deleteByPermId failed. permId=" + permId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_ui_permission deleteByPermId failed (unexpected). permId=" + permId, e);
        }
    }

    
    /**
     * DB MyBatis 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
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

package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpMapper;

/**
 * tc_eqp MyBatis Store 구현체.
 *
 * 목표
 * - app은 TcEqpStore(Port)만 알고 CRUD를 수행한다.
 * - 실제 DB 접근(MyBatis Mapper 호출)은 이 구현체가 담당한다.
 *
 * 업서트 전략(벤더 중립)
 * - "update 먼저" 시도 → 영향 0이면 insert 시도
 * - insert가 PK/UNIQUE 중복이면(동시성) 다시 update로 수렴
 */
@Repository
public class TcEqpMybatisStore implements TcEqpStore {

    private static final Logger log = LoggerFactory.getLogger(TcEqpMybatisStore.class);

    private final TcEqpMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcEqpMybatisStore(TcEqpMapper mapper) {
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
    public TcEqp upsert(UpsertTcEqp command) {
        validateCommand(command);

        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final String eqpId = command.eqpId();

        if (log.isDebugEnabled()) {
            log.debug("tc_eqp upsert 시작. eqpId={}, commInterface={}, commMode={}, routePartition={}",
                    command.eqpId(),
                    command.commInterface(),
                    command.commMode(),
                    command.routePartition());
        }

        // created_at/updated_at은 DB/default 또는 SQL(CURRENT_TIMESTAMP)에 위임한다.
        final TcEqp row = new TcEqp(
                null,
                eqpId,
                command.commInterface(),
                command.commMode(),
                command.isDev(),
                command.routePartition(),
                command.eqpIp(),
                command.eqpPort(),
                command.modelVersionKey(),
                command.appliedParamVersion(),
                command.enabled(),
                null,
                null,
                command.createdBy(),
                command.updatedBy()
        );

        try {
            // 1) update 먼저 시도
            int updated = mapper.update(row);

            // 2) update가 0이면 insert 시도
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    // 동시성으로 누군가 먼저 insert 한 경우 → update로 수렴
                    mapper.update(row);
                }
            }

            // 3) 최종 상태를 재조회하여 반환(타임스탬프 포함)
            return mapper.findByEqpId(eqpId)
                    .orElseThrow(() -> new DbAccessException("tc_eqp upsert succeeded but row not found. eqpId=" + eqpId));

        } catch (DuplicateKeyException e) {
            // update/insert 과정에서 유니크 제약을 건드린 경우
            throw new DbDuplicateKeyException("tc_eqp upsert duplicate key. eqpId=" + eqpId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp upsert failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp upsert failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqp> findByEqpId(String eqpId) {
        try {
            return mapper.findByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp findByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp findByEqpId failed (unexpected). eqpId=" + eqpId, e);
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
    public List<TcEqp> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            // FIX: DB 페이징 적용 (메모리 로딩 이슈 해결)
            return mapper.findAll(
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp findAll failed (unexpected).", e);
        }
    }

    /**
     * route_partition + enabled 조건으로 tc_eqp를 페이징 조회합니다.
     *
     * <p>Gateway 기동/재기동 시 owned partition 설비만 선별 로딩하기 위한 메서드입니다.</p>
     * <p>입력 목록이 비어 있으면 DB를 조회하지 않고 즉시 빈 결과를 반환합니다.</p>
     *
     * @param routePartitions 조회 대상 route_partition 목록
     * @param enabled enabled 필터 값
     * @param page 페이징 조건 (null 허용, null이면 기본 페이지 사용)
     * @return 조건에 일치하는 tc_eqp 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqp> findAllByRoutePartitionsAndEnabled(List<Integer> routePartitions, boolean enabled, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        if (routePartitions == null || routePartitions.isEmpty()) {
            log.info("tc_eqp route_partition 조건 조회를 건너뜁니다. 사유=빈 partition 목록, enabled={}", enabled);
            return List.of();
        }

        if (log.isDebugEnabled()) {
            log.debug("tc_eqp route_partition 조건 조회 시작. routePartitions={}, enabled={}, offset={}, limit={}",
                    routePartitions,
                    enabled,
                    p.offset(),
                    p.limit());
        }

        try {
            final List<TcEqp> rows = mapper.findAllByRoutePartitionsAndEnabled(
                    routePartitions,
                    enabled,
                    p.offset(),
                    p.limit()
            );

            if (log.isDebugEnabled()) {
                log.debug("tc_eqp route_partition 조건 조회 완료. resultCount={}", rows.size());
            }
            return rows;
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp findAllByRoutePartitionsAndEnabled failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp findAllByRoutePartitionsAndEnabled failed (unexpected).", e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            // 삭제는 멱등(idempotent)으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    /**
     * tc_eqp upsert 입력값의 최소 유효성을 검증합니다.
     *
     * <p>MyBatis Store는 DB 제약에만 의존하지 않고, 명확한 오류 메시지를 위해 핵심 필드를 선검증합니다.</p>
     *
     * @param command upsert 입력 모델
     */
    private void validateCommand(final UpsertTcEqp command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.commInterface() == null) throw new IllegalArgumentException("command.commInterface must not be null");
        if (command.commMode() == null || command.commMode().isBlank()) throw new IllegalArgumentException("command.commMode must not be null/blank");
        if (!"ACTIVE".equals(command.commMode()) && !"PASSIVE".equals(command.commMode())) {
            throw new IllegalArgumentException("command.commMode must be ACTIVE or PASSIVE");
        }
        if (command.routePartition() != null && command.routePartition() < 0) {
            throw new IllegalArgumentException("command.routePartition must be >= 0 when present");
        }
        if (command.eqpIp() == null || command.eqpIp().isBlank()) throw new IllegalArgumentException("command.eqpIp must not be null/blank");
        if (command.eqpPort() <= 0) throw new IllegalArgumentException("command.eqpPort must be > 0");
        if (command.modelVersionKey() <= 0) throw new IllegalArgumentException("command.modelVersionKey must be > 0");
    }
}

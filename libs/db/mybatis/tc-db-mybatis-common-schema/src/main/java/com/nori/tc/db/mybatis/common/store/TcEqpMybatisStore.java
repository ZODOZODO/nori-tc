package com.nori.tc.db.mybatis.common.store;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.TcEqpSearchCriteria;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.mybatis.common.mapper.TcEqpMapper;

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

    private final TcEqpMapper mapper;

    public TcEqpMybatisStore(TcEqpMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqp upsert(UpsertTcEqp command) {
        final String eqpId = command.eqpId();

        // created_at/updated_at은 DB/default 또는 SQL(CURRENT_TIMESTAMP)에 위임한다.
        final TcEqp row = new TcEqp(
                null,
                eqpId,
                command.commInterface(),
                command.eqpIp(),
                command.eqpPort(),
                command.modelKey(),
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

    @Override
    @Transactional(readOnly = true)
    public List<TcEqp> findAll(TcEqpSearchCriteria criteria, PageRequest page) {
        // Null safe
        final TcEqpSearchCriteria c = (criteria == null) ? TcEqpSearchCriteria.empty() : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            // FIX: DB 페이징 적용 (메모리 로딩 이슈 해결)
            return mapper.findAll(
                    c.commInterface(),
                    c.enabled(),
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp findAll failed (unexpected).", e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        try {
            // 삭제는 멱등(idempotent)으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }
}

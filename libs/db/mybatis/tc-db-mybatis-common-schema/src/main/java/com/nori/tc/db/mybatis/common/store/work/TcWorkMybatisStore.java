package com.nori.tc.db.mybatis.common.store.work;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.work.store.TcWorkStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWork;
import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkMapper;

/**
 * tc_work MyBatis Store 구현체.
 *
 * upsert 주의
 * - common-schema의 TcWorkMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (eqp_key, work_id)로 재조회하여 work_key를 확보한다.
 */
@Repository
public class TcWorkMybatisStore implements TcWorkStore {

    private final TcWorkMapper mapper;

    public TcWorkMybatisStore(TcWorkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWork upsert(UpsertTcWork command) {
        final Long workKey = command.workKey();
        final long eqpKey = command.eqpKey();
        final String workId = command.workId();

        final long resolvedKey = resolveKey(workKey, eqpKey, workId);

        final TcWork row = new TcWork(
                resolvedKey,
                eqpKey,
                workId,
                command.operatorId(),
                command.stepSeq(),
                command.workState(),
                command.startTime(),
                command.endTime(),
                command.mesMessage(),
                null,
                null
        );

        try {
            int updated = mapper.updateByWorkKey(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_work insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByEqpKeyAndWorkId(eqpKey, workId)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work upsert succeeded but row not found. key=" + eqpKey + "/" + workId
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work upsert duplicate (eqp_key, work_id). key=" + eqpKey + "/" + workId,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work upsert failed. key=" + eqpKey + "/" + workId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work upsert failed (unexpected). key=" + eqpKey + "/" + workId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWork> findByWorkKey(long workKey) {
        try {
            return mapper.findByWorkKey(workKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work findByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work findByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWork> findByEqpKeyAndWorkId(long eqpKey, String workId) {
        try {
            return mapper.findByEqpKeyAndWorkId(eqpKey, workId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work findByUnique failed. key=" + eqpKey + "/" + workId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work findByUnique failed (unexpected). key=" + eqpKey + "/" + workId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWork> findAllByEqpKey(long eqpKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkKey(long workKey) {
        try {
            mapper.deleteByWorkKey(workKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work deleteByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work deleteByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    private long resolveKey(Long workKey, long eqpKey, String workId) {
        if (workKey != null) {
            if (workKey <= 0) {
                throw new IllegalArgumentException("workKey must be > 0 when provided");
            }
            return workKey;
        }

        return mapper.findByEqpKeyAndWorkId(eqpKey, workId)
                .map(TcWork::workKey)
                .orElse(0L);
    }
}

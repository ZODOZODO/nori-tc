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
import com.nori.tc.db.core.work.store.TcWorkLotStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkLot;
import com.nori.tc.db.domain.work.TcWorkLot;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkLotMapper;

/**
 * tc_work_lot MyBatis Store 구현체.
 *
 * - Unique(work_key, lot_id)를 기준으로 update-first upsert 전략을 사용한다.
 * - updated_at 은 SQL에서 CURRENT_TIMESTAMP 로 처리한다.
 */
@Repository
public class TcWorkLotMybatisStore implements TcWorkLotStore {

    private final TcWorkLotMapper mapper;

    public TcWorkLotMybatisStore(TcWorkLotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkLot upsert(UpsertTcWorkLot command) {
        final long workKey = command.workKey();
        final String lotId = command.lotId();

        final TcWorkLot row = new TcWorkLot(
                0L,
                workKey,
                command.carrierId(),
                lotId,
                command.parentLotId(),
                command.chamberId(),
                null
        );

        try {
            int updated = mapper.updateByUniqueKey(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.updateByUniqueKey(row);
                }
            }

            return mapper.findByWorkKeyAndLotId(workKey, lotId)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_lot upsert succeeded but row not found. workKey=" + workKey + ", lotId=" + lotId
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_lot upsert duplicate key. workKey=" + workKey + ", lotId=" + lotId,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_lot upsert failed. workKey=" + workKey + ", lotId=" + lotId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_lot upsert failed (unexpected). workKey=" + workKey + ", lotId=" + lotId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkLot> findByWorkKeyAndLotId(long workKey, String lotId) {
        try {
            return mapper.findByWorkKeyAndLotId(workKey, lotId);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_lot findByWorkKeyAndLotId failed. workKey=" + workKey + ", lotId=" + lotId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_lot findByWorkKeyAndLotId failed (unexpected). workKey=" + workKey + ", lotId=" + lotId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkLot> findAllByWorkKey(long workKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkKey(workKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_lot findAllByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_lot findAllByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkLotKey(long workLotKey) {
        try {
            mapper.deleteByWorkLotKey(workLotKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_lot deleteByWorkLotKey failed. workLotKey=" + workLotKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_lot deleteByWorkLotKey failed (unexpected). workLotKey=" + workLotKey, e);
        }
    }
}

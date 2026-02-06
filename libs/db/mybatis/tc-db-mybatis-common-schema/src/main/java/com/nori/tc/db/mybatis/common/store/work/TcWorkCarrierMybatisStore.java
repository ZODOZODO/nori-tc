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
import com.nori.tc.db.core.work.store.TcWorkCarrierStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkCarrierMapper;

/**
 * tc_work_carrier MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (work_key, carrier_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkCarrierMybatisStore implements TcWorkCarrierStore {

    private final TcWorkCarrierMapper mapper;

    public TcWorkCarrierMybatisStore(TcWorkCarrierMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkCarrier upsert(UpsertTcWorkCarrier command) {
        validateCommand(command);

        final long workKey = command.workKey();
        final String carrierId = command.carrierId();

        final TcWorkCarrier row = new TcWorkCarrier(
                0L,
                workKey,
                carrierId,
                command.portId(),
                command.slotMap(),
                command.totalQty(),
                command.goodQty(),
                command.scrapQty(),
                command.updatedAt() // SQL에서는 CURRENT_TIMESTAMP로 갱신(입력값은 참고용)
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

            return mapper.findByWorkKeyCarrierId(workKey, carrierId)
                    .orElseThrow(() -> new DbAccessException("tc_work_carrier upsert succeeded but row not found. workKey/carrierId=" + workKey + "/" + carrierId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_work_carrier upsert duplicate key. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier upsert failed. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier upsert failed (unexpected). workKey/carrierId=" + workKey + "/" + carrierId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkCarrier> findByWorkKeyCarrierId(long workKey, String carrierId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            return mapper.findByWorkKeyCarrierId(workKey, carrierId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier findByWorkKeyCarrierId failed. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier findByWorkKeyCarrierId failed (unexpected). workKey/carrierId=" + workKey + "/" + carrierId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkCarrier> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkKey(workKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier findAllByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier findAllByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkKeyCarrierId(long workKey, String carrierId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            mapper.deleteByWorkKeyCarrierId(workKey, carrierId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_carrier deleteByWorkKeyCarrierId failed. workKey/carrierId=" + workKey + "/" + carrierId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_carrier deleteByWorkKeyCarrierId failed (unexpected). workKey/carrierId=" + workKey + "/" + carrierId, e);
        }
    }

    private void validateCommand(UpsertTcWorkCarrier command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workKey() <= 0) throw new IllegalArgumentException("command.workKey must be > 0");
        if (command.carrierId() == null || command.carrierId().isBlank()) throw new IllegalArgumentException("command.carrierId must not be null/blank");
    }
}

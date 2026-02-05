package com.nori.tc.db.mybatis.common.store.eqp;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStateHistStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpStateHist;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpStateHistMapper;

/**
 * tc_eqp_state_hist MyBatis Store 구현체.
 *
 * - 이력 테이블이므로 append(insert)만 제공
 */
@Repository
public class TcEqpStateHistMybatisStore implements TcEqpStateHistStore {

    private final TcEqpStateHistMapper mapper;

    public TcEqpStateHistMybatisStore(TcEqpStateHistMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void append(UpsertTcEqpStateHist command) {
        UpsertTcEqpStateHist normalized = normalizeCommand(command);
        validateCommand(normalized);

        final TcEqpStateHist row = new TcEqpStateHist(
                0L,
                normalized.eqpKey(),
                normalized.stateType(),
                normalized.fromState(),
                normalized.toState(),
                normalized.changedAt(),
                normalized.reasonCode(),
                normalized.reasonDetail()
        );

        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_state_hist append duplicate key. eqpKey=" + normalized.eqpKey(), e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state_hist append failed. eqpKey=" + normalized.eqpKey(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state_hist append failed (unexpected). eqpKey=" + normalized.eqpKey(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpStateHist> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, p.limit(), p.offset());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state_hist findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state_hist findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    private void validateCommand(UpsertTcEqpStateHist command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.eqpKey() == null || command.eqpKey() <= 0) {
            throw new IllegalArgumentException("command.eqpKey must be positive");
        }
        if (command.stateType() == null) {
            throw new IllegalArgumentException("command.stateType must not be null");
        }
    }

    private UpsertTcEqpStateHist normalizeCommand(UpsertTcEqpStateHist command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcEqpStateHist(
                command.eqpKey(),
                command.stateType(),
                command.fromState(),
                command.toState(),
                command.changedAt() == null ? OffsetDateTime.now() : command.changedAt(),
                command.reasonCode(),
                command.reasonDetail()
        );
    }
}

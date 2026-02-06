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
import com.nori.tc.db.core.work.store.TcWorkControlJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkControlJobMapper;

/**
 * tc_work_controljob MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (work_key, controljob_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkControlJobMybatisStore implements TcWorkControlJobStore {

    private final TcWorkControlJobMapper mapper;

    public TcWorkControlJobMybatisStore(TcWorkControlJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkControlJob upsert(UpsertTcWorkControlJob command) {
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcWorkControlJob row = new TcWorkControlJob(
                resolvedKey,
                command.workKey(),
                command.controljobId(),
                command.controljobState(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_work_controljob insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByWorkKeyAndControljobId(command.workKey(), command.controljobId())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_controljob upsert succeeded but row not found. key=" + command.workKey() + "/" + command.controljobId()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_controljob upsert duplicate (work_key, controljob_id). key=" + command.workKey() + "/" + command.controljobId(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_controljob upsert failed. key=" + command.workKey() + "/" + command.controljobId(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_controljob upsert failed (unexpected). key=" + command.workKey() + "/" + command.controljobId(),
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByControlJobKey(long controlJobKey) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            return mapper.findByControlJobKey(controlJobKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob findByControlJobKey failed. controlJobKey=" + controlJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob findByControlJobKey failed (unexpected). controlJobKey=" + controlJobKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByWorkKeyAndControljobId(long workKey, String controljobId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (controljobId == null || controljobId.isBlank()) {
            throw new IllegalArgumentException("controljobId must not be null/blank");
        }
        try {
            return mapper.findByWorkKeyAndControljobId(workKey, controljobId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob findByWorkKeyAndControljobId failed. key=" + workKey + "/" + controljobId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob findByWorkKeyAndControljobId failed (unexpected). key=" + workKey + "/" + controljobId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkControlJob> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByWorkKey(workKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob findAllByWorkKey failed. workKey=" + workKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob findAllByWorkKey failed (unexpected). workKey=" + workKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByControlJobKey(long controlJobKey) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            mapper.deleteByControlJobKey(controlJobKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_controljob deleteByControlJobKey failed. controlJobKey=" + controlJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_controljob deleteByControlJobKey failed (unexpected). controlJobKey=" + controlJobKey, e);
        }
    }

    private void validateCommand(UpsertTcWorkControlJob command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.controlJobKey() != null && command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0 when provided");
        }
        if (command.workKey() <= 0) {
            throw new IllegalArgumentException("command.workKey must be > 0");
        }
        if (command.controljobId() == null || command.controljobId().isBlank()) {
            throw new IllegalArgumentException("command.controljobId must not be null/blank");
        }
        if (command.controljobState() == null) {
            throw new IllegalArgumentException("command.controljobState must not be null");
        }
    }

    private long resolveKey(UpsertTcWorkControlJob command) {
        if (command.controlJobKey() != null) {
            return command.controlJobKey();
        }

        return mapper.findByWorkKeyAndControljobId(command.workKey(), command.controljobId())
                .map(TcWorkControlJob::controlJobKey)
                .orElse(0L);
    }
}

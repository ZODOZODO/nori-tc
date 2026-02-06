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
import com.nori.tc.db.core.work.store.TcWorkProcessJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessJob;
import com.nori.tc.db.domain.work.TcWorkProcessJob;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkProcessJobMapper;

/**
 * tc_work_processjob MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (control_job_key, processjob_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkProcessJobMybatisStore implements TcWorkProcessJobStore {

    private final TcWorkProcessJobMapper mapper;

    public TcWorkProcessJobMybatisStore(TcWorkProcessJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkProcessJob upsert(UpsertTcWorkProcessJob command) {
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcWorkProcessJob row = new TcWorkProcessJob(
                resolvedKey,
                command.controlJobKey(),
                command.processjobId(),
                command.processjobState(),
                command.recipeId(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_work_processjob insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByControlJobKeyAndProcessjobId(command.controlJobKey(), command.processjobId())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_processjob upsert succeeded but row not found. key="
                                    + command.controlJobKey() + "/" + command.processjobId()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_processjob upsert duplicate (control_job_key, processjob_id). key="
                            + command.controlJobKey() + "/" + command.processjobId(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_processjob upsert failed. key="
                            + command.controlJobKey() + "/" + command.processjobId(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_processjob upsert failed (unexpected). key="
                            + command.controlJobKey() + "/" + command.processjobId(),
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessJob> findByProcessJobKey(long processJobKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        try {
            return mapper.findByProcessJobKey(processJobKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob findByProcessJobKey failed. processJobKey=" + processJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob findByProcessJobKey failed (unexpected). processJobKey=" + processJobKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessJob> findByControlJobKeyAndProcessjobId(long controlJobKey, String processjobId) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        if (processjobId == null || processjobId.isBlank()) {
            throw new IllegalArgumentException("processjobId must not be null/blank");
        }
        try {
            return mapper.findByControlJobKeyAndProcessjobId(controlJobKey, processjobId);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_processjob findByControlJobKeyAndProcessjobId failed. key="
                            + controlJobKey + "/" + processjobId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_processjob findByControlJobKeyAndProcessjobId failed (unexpected). key="
                            + controlJobKey + "/" + processjobId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkProcessJob> findAllByControlJobKey(long controlJobKey, PageRequest page) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByControlJobKey(controlJobKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob findAllByControlJobKey failed. controlJobKey=" + controlJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob findAllByControlJobKey failed (unexpected). controlJobKey=" + controlJobKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByProcessJobKey(long processJobKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        try {
            mapper.deleteByProcessJobKey(processJobKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob deleteByProcessJobKey failed. processJobKey=" + processJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob deleteByProcessJobKey failed (unexpected). processJobKey=" + processJobKey, e);
        }
    }

    private void validateCommand(UpsertTcWorkProcessJob command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.processJobKey() != null && command.processJobKey() <= 0) {
            throw new IllegalArgumentException("command.processJobKey must be > 0 when provided");
        }
        if (command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0");
        }
        if (command.processjobId() == null || command.processjobId().isBlank()) {
            throw new IllegalArgumentException("command.processjobId must not be null/blank");
        }
        if (command.processjobState() == null) {
            throw new IllegalArgumentException("command.processjobState must not be null");
        }
        if (command.recipeId() == null || command.recipeId().isBlank()) {
            throw new IllegalArgumentException("command.recipeId must not be null/blank");
        }
    }

    private long resolveKey(UpsertTcWorkProcessJob command) {
        if (command.processJobKey() != null) {
            return command.processJobKey();
        }

        return mapper.findByControlJobKeyAndProcessjobId(command.controlJobKey(), command.processjobId())
                .map(TcWorkProcessJob::processJobKey)
                .orElse(0L);
    }
}

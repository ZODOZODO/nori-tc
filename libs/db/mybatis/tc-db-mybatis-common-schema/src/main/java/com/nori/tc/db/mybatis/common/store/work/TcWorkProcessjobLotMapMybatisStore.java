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
import com.nori.tc.db.core.work.store.TcWorkProcessjobLotMapStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessjobLotMap;
import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;
import com.nori.tc.db.mybatis.common.mapper.work.TcWorkProcessjobLotMapMapper;

/**
 * tc_work_processjob_lot_map MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (process_job_key, work_lot_key)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcWorkProcessjobLotMapMybatisStore implements TcWorkProcessjobLotMapStore {

    private final TcWorkProcessjobLotMapMapper mapper;

    public TcWorkProcessjobLotMapMybatisStore(TcWorkProcessjobLotMapMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkProcessjobLotMap upsert(UpsertTcWorkProcessjobLotMap command) {
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcWorkProcessjobLotMap row = new TcWorkProcessjobLotMap(
                resolvedKey,
                command.processJobKey(),
                command.workLotKey(),
                command.mapRole(),
                command.mapOrder(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_work_processjob_lot_map insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByProcessJobKeyAndWorkLotKey(command.processJobKey(), command.workLotKey())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_work_processjob_lot_map upsert succeeded but row not found. key="
                                    + command.processJobKey() + "/" + command.workLotKey()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_work_processjob_lot_map upsert duplicate (process_job_key, work_lot_key). key="
                            + command.processJobKey() + "/" + command.workLotKey(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map upsert failed. key="
                            + command.processJobKey() + "/" + command.workLotKey(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map upsert failed (unexpected). key="
                            + command.processJobKey() + "/" + command.workLotKey(),
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessjobLotMap> findByPjLotMapKey(long pjLotMapKey) {
        if (pjLotMapKey <= 0) {
            throw new IllegalArgumentException("pjLotMapKey must be > 0");
        }
        try {
            return mapper.findByPjLotMapKey(pjLotMapKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findByPjLotMapKey failed. pjLotMapKey=" + pjLotMapKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findByPjLotMapKey failed (unexpected). pjLotMapKey=" + pjLotMapKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessjobLotMap> findByProcessJobKeyAndWorkLotKey(long processJobKey, long workLotKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        if (workLotKey <= 0) {
            throw new IllegalArgumentException("workLotKey must be > 0");
        }
        try {
            return mapper.findByProcessJobKeyAndWorkLotKey(processJobKey, workLotKey);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map findByUniqueKey failed. key=" + processJobKey + "/" + workLotKey,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_work_processjob_lot_map findByUniqueKey failed (unexpected). key=" + processJobKey + "/" + workLotKey,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkProcessjobLotMap> findAllByProcessJobKey(long processJobKey, PageRequest page) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByProcessJobKey(processJobKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findAllByProcessJobKey failed. processJobKey=" + processJobKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob_lot_map findAllByProcessJobKey failed (unexpected). processJobKey=" + processJobKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByPjLotMapKey(long pjLotMapKey) {
        if (pjLotMapKey <= 0) {
            throw new IllegalArgumentException("pjLotMapKey must be > 0");
        }
        try {
            mapper.deleteByPjLotMapKey(pjLotMapKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_work_processjob_lot_map deleteByPjLotMapKey failed. pjLotMapKey=" + pjLotMapKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_work_processjob_lot_map deleteByPjLotMapKey failed (unexpected). pjLotMapKey=" + pjLotMapKey, e);
        }
    }

    private void validateCommand(UpsertTcWorkProcessjobLotMap command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.pjLotMapKey() != null && command.pjLotMapKey() <= 0) {
            throw new IllegalArgumentException("command.pjLotMapKey must be > 0 when provided");
        }
        if (command.processJobKey() <= 0) {
            throw new IllegalArgumentException("command.processJobKey must be > 0");
        }
        if (command.workLotKey() <= 0) {
            throw new IllegalArgumentException("command.workLotKey must be > 0");
        }
    }

    private long resolveKey(UpsertTcWorkProcessjobLotMap command) {
        if (command.pjLotMapKey() != null) {
            return command.pjLotMapKey();
        }

        return mapper.findByProcessJobKeyAndWorkLotKey(command.processJobKey(), command.workLotKey())
                .map(TcWorkProcessjobLotMap::pjLotMapKey)
                .orElse(0L);
    }
}

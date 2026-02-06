package com.nori.tc.db.mybatis.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelReportIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelReportId;
import com.nori.tc.db.domain.model.TcModelReportId;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelReportIdMapper;

/**
 * tc_model_reportid MyBatis Store 구현체.
 */
@Repository
public class TcModelReportIdMybatisStore implements TcModelReportIdStore {

    private final TcModelReportIdMapper mapper;

    public TcModelReportIdMybatisStore(TcModelReportIdMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelReportId upsert(UpsertTcModelReportId command) {
        validateUpsert(command);

        final long modelKey = command.modelKey();
        final String reportId = command.reportId();

        // [FIX] 도메인 모델 시그니처에 맞춰 variableId/updatedAt만 채운다.
        // - 기존 구현은 reportName/createdAt/updatedAt 필드를 참조해 컴파일 오류가 발생했다.
        final TcModelReportId row = new TcModelReportId(
                0L,
                modelKey,
                reportId,
                command.variableId(),
                command.enabled(),
                null
        );

        try {
            int updated = mapper.updateByUniqueKey(row);
            if (updated == 0) {
                mapper.insert(row);
            }
            return mapper.findByModelKeyAndReportId(modelKey, reportId)
                    .orElseThrow(() -> new DbAccessException("tc_model_reportid upsert succeeded but row not found. modelKey/reportId=" + modelKey + "/" + reportId));
        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_reportid duplicate key. modelKey/reportId=" + modelKey + "/" + reportId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid upsert failed. modelKey/reportId=" + modelKey + "/" + reportId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelReportId> findByReportKey(long reportKey) {
        try {
            return mapper.findByReportKey(reportKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid findByReportKey failed. reportKey=" + reportKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid findByReportKey failed (unexpected). reportKey=" + reportKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelReportId> findByModelKeyAndReportId(long modelKey, String reportId) {
        try {
            return mapper.findByModelKeyAndReportId(modelKey, reportId);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_reportid findByModelKeyAndReportId failed. modelKey=" + modelKey + ", reportId=" + reportId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_reportid findByModelKeyAndReportId failed (unexpected). modelKey=" + modelKey + ", reportId=" + reportId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelReportId> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(
                    modelKey,
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid findAllByModelKey failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid findAllByModelKey failed (unexpected).", e);
        }
    }

    @Override
    @Transactional
    public void deleteByReportKey(long reportKey) {
        try {
            mapper.deleteByReportKey(reportKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid deleteByReportKey failed. reportKey=" + reportKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid deleteByReportKey failed (unexpected). reportKey=" + reportKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelReportId command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.reportId() == null || command.reportId().isBlank()) {
            throw new IllegalArgumentException("command.reportId must not be null/blank");
        }
    }
}

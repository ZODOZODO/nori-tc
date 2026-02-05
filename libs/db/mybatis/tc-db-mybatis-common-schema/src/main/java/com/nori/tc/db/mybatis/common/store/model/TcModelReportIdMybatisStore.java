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
import com.nori.tc.db.core.model.TcModelReportIdSearchCriteria;
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
        final long modelKey = command.modelKey();
        final String reportId = command.reportId();

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
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.updateByUniqueKey(row);
                }
            }

            return mapper.findByModelKeyAndReportId(modelKey, reportId)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_reportid upsert succeeded but row not found. modelKey=" + modelKey + ", reportId=" + reportId
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_reportid upsert duplicate key. modelKey=" + modelKey + ", reportId=" + reportId,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_reportid upsert failed. modelKey=" + modelKey + ", reportId=" + reportId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_reportid upsert failed (unexpected). modelKey=" + modelKey + ", reportId=" + reportId,
                    e
            );
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
    public List<TcModelReportId> findAll(TcModelReportIdSearchCriteria criteria, PageRequest page) {
        final TcModelReportIdSearchCriteria c = (criteria == null)
                ? new TcModelReportIdSearchCriteria(null, null, null)
                : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(
                    c.modelKey(),
                    c.reportId(),
                    c.enabled(),
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_reportid findAll failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_reportid findAll failed (unexpected).", e);
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
}

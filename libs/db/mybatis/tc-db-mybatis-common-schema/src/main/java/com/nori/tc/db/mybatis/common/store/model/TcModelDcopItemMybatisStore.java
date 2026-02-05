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
import com.nori.tc.db.core.model.TcModelDcopItemSearchCriteria;
import com.nori.tc.db.core.model.store.TcModelDcopItemStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelDcopItem;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelDcopItemMapper;

/**
 * tc_model_dcop_item MyBatis Store 구현체.
 */
@Repository
public class TcModelDcopItemMybatisStore implements TcModelDcopItemStore {

    private final TcModelDcopItemMapper mapper;

    public TcModelDcopItemMybatisStore(TcModelDcopItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelDcopItem upsert(UpsertTcModelDcopItem command) {
        validateUpsert(command);

        final TcModelDcopItem row = new TcModelDcopItem(
                null,
                command.modelKey(),
                command.dcopItemName(),
                command.workflowName(),
                command.eventId(),
                command.variableId(),
                command.collectionRule(),
                command.calculationRule(),
                command.orderRule(),
                null
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

            return mapper.findByModelKeyAndName(command.modelKey(), command.dcopItemName())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_dcop_item upsert succeeded but row not found. modelKey="
                                    + command.modelKey() + ", dcopItemName=" + command.dcopItemName()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_dcop_item upsert duplicate key. modelKey="
                            + command.modelKey() + ", dcopItemName=" + command.dcopItemName(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_dcop_item upsert failed. modelKey="
                            + command.modelKey() + ", dcopItemName=" + command.dcopItemName(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_dcop_item upsert failed (unexpected). modelKey="
                            + command.modelKey() + ", dcopItemName=" + command.dcopItemName(),
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelDcopItem> findByModelKeyAndName(long modelKey, String dcopItemName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (dcopItemName == null || dcopItemName.isBlank()) {
            throw new IllegalArgumentException("dcopItemName must not be null/blank");
        }
        try {
            return mapper.findByModelKeyAndName(modelKey, dcopItemName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item findByModelKeyAndName failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_dcop_item findByModelKeyAndName failed (unexpected)", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelDcopItem> findAll(TcModelDcopItemSearchCriteria criteria, PageRequest page) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria must not be null");
        }
        if (criteria.modelKey() <= 0) {
            throw new IllegalArgumentException("criteria.modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(criteria.modelKey(), p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item findAll failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_dcop_item findAll failed (unexpected)", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelKeyAndName(long modelKey, String dcopItemName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (dcopItemName == null || dcopItemName.isBlank()) {
            throw new IllegalArgumentException("dcopItemName must not be null/blank");
        }
        try {
            mapper.deleteByModelKeyAndName(modelKey, dcopItemName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_dcop_item deleteByModelKeyAndName failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_dcop_item deleteByModelKeyAndName failed (unexpected)", e);
        }
    }

    private void validateUpsert(UpsertTcModelDcopItem command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.dcopItemName() == null || command.dcopItemName().isBlank()) {
            throw new IllegalArgumentException("command.dcopItemName must not be null/blank");
        }
        if (command.orderRule() != null && command.orderRule() < 0) {
            throw new IllegalArgumentException("command.orderRule must be >= 0");
        }
    }
}

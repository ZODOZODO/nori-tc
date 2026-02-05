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
import com.nori.tc.db.core.model.store.TcModelVariableIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelVariableId;
import com.nori.tc.db.domain.common.VariableIdType;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelVariableIdMapper;

/**
 * tc_model_variableid MyBatis Store 구현체.
 */
@Repository
public class TcModelVariableIdMybatisStore implements TcModelVariableIdStore {

    private final TcModelVariableIdMapper mapper;

    public TcModelVariableIdMybatisStore(TcModelVariableIdMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelVariableId upsert(UpsertTcModelVariableId command) {
        final long modelKey = command.modelKey();
        final VariableIdType variableIdType = command.variableIdType();
        final String variableId = command.variableId();

        final TcModelVariableId row = new TcModelVariableId(
                0L,
                modelKey,
                variableId,
                variableIdType,
                command.description(),
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

            return mapper.findByModelKeyAndTypeAndVariableId(modelKey, variableIdType, variableId)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_variableid upsert succeeded but row not found. modelKey=" + modelKey
                                    + ", variableIdType=" + variableIdType + ", variableId=" + variableId
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_variableid upsert duplicate key. modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_variableid upsert failed. modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_variableid upsert failed (unexpected). modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelVariableId> findByVariableKey(long variableKey) {
        try {
            return mapper.findByVariableKey(variableKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_variableid findByVariableKey failed. variableKey=" + variableKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_variableid findByVariableKey failed (unexpected). variableKey=" + variableKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelVariableId> findByModelKeyAndTypeAndVariableId(
            long modelKey,
            VariableIdType variableIdType,
            String variableId
    ) {
        try {
            return mapper.findByModelKeyAndTypeAndVariableId(modelKey, variableIdType, variableId);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_variableid findByModelKeyAndTypeAndVariableId failed. modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_variableid findByModelKeyAndTypeAndVariableId failed (unexpected). modelKey=" + modelKey
                            + ", variableIdType=" + variableIdType + ", variableId=" + variableId,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelVariableId> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_variableid findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_variableid findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByVariableKey(long variableKey) {
        try {
            mapper.deleteByVariableKey(variableKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_variableid deleteByVariableKey failed. variableKey=" + variableKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_variableid deleteByVariableKey failed (unexpected). variableKey=" + variableKey, e);
        }
    }
}

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
import com.nori.tc.db.core.model.store.TcModelParamStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelParam;
import com.nori.tc.db.domain.model.TcModelParam;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelParamMapper;

/**
 * tc_model_param MyBatis Store 구현체.
 */
@Repository
public class TcModelParamMybatisStore implements TcModelParamStore {

    private final TcModelParamMapper mapper;

    public TcModelParamMybatisStore(TcModelParamMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelParam upsert(UpsertTcModelParam command) {
        final long modelKey = command.modelKey();
        final String paramName = command.paramName();

        final TcModelParam row = new TcModelParam(
                0L,
                modelKey,
                paramName,
                command.paramValue(),
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

            return mapper.findByModelKeyAndName(modelKey, paramName)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_param upsert succeeded but row not found. modelKey=" + modelKey + ", param=" + paramName
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_param upsert duplicate key. modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_param upsert failed. modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_param upsert failed (unexpected). modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelParam> findByModelKeyAndName(long modelKey, String paramName) {
        try {
            return mapper.findByModelKeyAndName(modelKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_param findByModelKeyAndName failed. modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_param findByModelKeyAndName failed (unexpected). modelKey=" + modelKey + ", param=" + paramName,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelParam> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_param findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_param findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelParamKey(long modelParamKey) {
        try {
            mapper.deleteByModelParamKey(modelParamKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_param deleteByModelParamKey failed. modelParamKey=" + modelParamKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_param deleteByModelParamKey failed (unexpected). modelParamKey=" + modelParamKey, e);
        }
    }
}

package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParam;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpParamMapper;

/**
 * tc_eqp_param MyBatis Store 구현체.
 */
@Repository
public class TcEqpParamMybatisStore implements TcEqpParamStore {

    private final TcEqpParamMapper mapper;

    public TcEqpParamMybatisStore(TcEqpParamMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpParam upsert(UpsertTcEqpParam command) {
        final long eqpKey = command.eqpKey();
        final String paramName = command.paramName();
        final String paramVersion = command.paramVersion();

        final TcEqpParam row = new TcEqpParam(
                0L,
                eqpKey,
                paramName,
                paramVersion,
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

            return mapper.findByEqpKeyAndNameVersion(eqpKey, paramName, paramVersion)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_eqp_param upsert succeeded but row not found. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_eqp_param upsert duplicate key. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_eqp_param upsert failed. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_eqp_param upsert failed (unexpected). eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpParam> findByEqpKeyAndNameVersion(long eqpKey, String paramName, String paramVersion) {
        try {
            return mapper.findByEqpKeyAndNameVersion(eqpKey, paramName, paramVersion);
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_eqp_param findByEqpKeyAndNameVersion failed. eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_eqp_param findByEqpKeyAndNameVersion failed (unexpected). eqpKey=" + eqpKey + ", param=" + paramName + "/" + paramVersion,
                    e
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpParam> findAllByEqpKey(long eqpKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_param findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_param findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpParamKey(long eqpParamKey) {
        try {
            mapper.deleteByEqpParamKey(eqpParamKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_param deleteByEqpParamKey failed. eqpParamKey=" + eqpParamKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_param deleteByEqpParamKey failed (unexpected). eqpParamKey=" + eqpParamKey, e);
        }
    }
}
